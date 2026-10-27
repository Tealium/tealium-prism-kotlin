#!/usr/bin/env python3
"""Summarise the records phases.init.gradle.kts writes: per Gradle build, the phase durations and task timings.

Run by the "Gradle phase summary" steps of kotlin-unit-tests.yml (unit and instrumented jobs) and ci-cache-warm.yml.
usage: python3 -I phases_summary.py [file]   (default: $RUNNER_TEMP/gradle-phases.tsv)
The record format is described in the header of phases.init.gradle.kts.
Writes the same markdown to stdout and to $GITHUB_STEP_SUMMARY. Never fails the job (exit code 0).
"""
import collections
import os
import sys

Row = collections.namedtuple("Row", "kind id name start end outcome detail")
PHASES = [  # (label, from phase, to phase)
    ("Settings", "start", "settings"),
    ("Load projects", "settings", "projectsLoaded"),
    ("buildSrc + build-logic classpath", "projectsLoaded", "configureStart"),
    ("Configure projects", "configureStart", "projectsEvaluated"),
    ("Task graph", "projectsEvaluated", "taskGraph"),
]
TOP = 10


def secs(ms):
    return f"{ms / 1000:.1f}"


def read(path):
    rows = []
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            cols = [None if c == "-" else c for c in line.rstrip("\n").split("\t")]
            try:
                if len(cols) == 7 and cols[0] in ("phase", "task", "build", "end"):
                    rows.append(Row(*cols[:3], *(int(c) if c else None for c in cols[3:5]), *cols[5:]))
            except ValueError:
                continue  # a line truncated by a killed build
    return rows


def open_build(out, detail=None):
    out.append({"phases": {}, "detail": detail, "run": None, "tasks": [], "end": None})
    return out[-1]


def builds(rows):
    """Builds in file order: {"phases": {name: t}, "detail": tasks, "run": id, "tasks": [Row], "end": Row}.
    Builds run one after another, configuration before execution, so a build's rows are contiguous: a `start` phase
    row opens a build (a miss), else the `build` row does (a hit; it precedes the build's first task row); a task row
    joins the open build and an end row closes it."""
    out, cur = [], None
    for r in rows:
        if r.kind == "phase":
            if r.name == "start":
                cur = open_build(out, r.detail)
            if cur:
                cur["phases"].setdefault(r.name, r.start)
        elif r.kind == "build":
            if cur is None or cur["run"] is not None or cur["end"]:
                cur = open_build(out)
            cur["run"], cur["detail"] = r.id, r.name
        elif r.kind == "task":
            cur = cur or open_build(out)
            cur["tasks"].append(r)
        else:  # end
            if cur is None or cur["end"]:
                cur = open_build(out)
            cur["end"] = r
    return out


def table(header, rows, right=(1, 2)):
    return ["| " + " | ".join(header) + " |",
            "|" + "|".join("---:" if i in right else "---" for i in range(len(header))) + "|",
            *("| " + " | ".join(row) + " |" for row in rows), ""]


def phase_rows(p, tasks, end, until):
    rows = []
    bsrc = [t for t in tasks if t.name.startswith(":buildSrc:")]
    for label, a, z in PHASES:
        if a in p and z in p:
            rows.append([label, secs(p[z] - p[a])])
            if z == "configureStart" and bsrc:  # buildSrc's tasks run inside that phase
                first = min(t.start for t in bsrc)
                rows += [["– of which buildSrc configuration", secs(first - p["projectsLoaded"])],
                         ["– of which buildSrc tasks + classpath", secs(p["configureStart"] - first)]]
    if "taskGraph" in p and end:
        rows.append([f"Execute tasks (+ CC store), to the {until}", secs(end - p["taskGraph"])])
    if "taskGraph" not in p:
        rows.append(["Configuration did not finish", "-"])
    if end:
        rows.append([f"**Total, to the {until}**", secs(end - p["start"])])
    return rows


def task_tables(tasks):
    ms = {t: t.end - t.start for t in tasks}
    by_outcome, by_name = collections.Counter(), collections.defaultdict(lambda: [0, 0])
    total = collections.Counter()
    for t in tasks:
        key = t.outcome + (f" ({t.detail})" if t.detail else "")
        by_outcome[key] += 1
        total[key] += ms[t]
        n = by_name[t.name.rsplit(":", 1)[-1]]
        n[0] += 1
        n[1] += ms[t]
    lines = table(["Task outcome", "Tasks", "Total (s)"],
                  [[k, str(c), secs(total[k])] for k, c in sorted(by_outcome.items())])
    ranked = sorted(by_name.items(), key=lambda kv: (-kv[1][1], kv[0]))
    rows = [[f"`{n}`", str(c), secs(t)] for n, (c, t) in ranked[:TOP]]
    if ranked[TOP:]:
        rest = ranked[TOP:]
        rows.append([f"other ({len(rest)} names)", str(sum(c for _, (c, _) in rest)),
                     secs(sum(t for _, (_, t) in rest))])
    lines += table(["Task name (all modules)", "Tasks", "Total (s)"], rows)
    slow = sorted(tasks, key=lambda t: (-ms[t], t.name))[:TOP]
    return lines + table(["Slowest tasks", "Time (s)", "Outcome"],
                         [[f"`{t.name}`", secs(ms[t]), t.outcome] for t in slow], (1,))


def notes(p, tasks, end, until):
    execute = [t for t in tasks if "taskGraph" not in p or t.start >= p["taskGraph"]]
    lines = []
    if "taskGraph" in p and execute:
        lines += [f"Configuration-cache store + reload: {secs(min(t.start for t in execute) - p['taskGraph'])} s "
                  f"(taskGraph to the first task start)", ""]
    if execute:
        first = p.get("taskGraph", min(t.start for t in execute))
        lines += [f"Task time {secs(sum(t.end - t.start for t in execute))} s over {len(execute)} tasks "
                  f"vs execute wall time {secs((end or max(t.end for t in execute)) - first)} s (to the {until})", ""]
    if len(execute) != len(tasks):
        lines += [f"{len(tasks) - len(execute)} task(s) ran before taskGraph (buildSrc).", ""]
    return lines


def render_build(b):
    p, tasks = b["phases"], b["tasks"]
    hit = "start" not in p
    # Only a problem is spelled out: a failed build, or one with no end row (killed?) whose outcome is unknown.
    outcome = "" if b["end"] and b["end"].outcome != "FAILED" else (
        "FAILED, " if b["end"] else "outcome unknown (no end record), ")
    end, until = ((b["end"].end, "build end") if b["end"]
                  else (max((t.end for t in tasks), default=None), "last task end"))
    what = f"`{b['detail']}`" if b["detail"] is not None else "(requested tasks not recorded)"
    lines = [f"#### Gradle {what}: {outcome}configuration cache {'hit' if hit else 'miss'}", ""]
    if not hit:
        lines += table(["Phase", "Duration (s)"], phase_rows(p, tasks, end, until))
    if not tasks:
        return lines + ["No task finished.", ""]
    return lines + task_tables(tasks) + notes(p, tasks, end, until)


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.environ.get("RUNNER_TEMP", "."), "gradle-phases.tsv")
    try:
        found = builds(read(path))
        lines = ["### Gradle phases", ""] + [l for b in found for l in render_build(b)]
        if not found:
            lines.append(f"No records in `{path}`.")
    except Exception as e:  # never fail the job over the summary
        lines = ["### Gradle phases", "", f"No summary ({type(e).__name__}: {e}; file `{path}`)."]
    md = "\n".join(lines) + "\n"
    sys.stdout.write(md)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        try:
            with open(summary, "a", encoding="utf-8") as f:
                f.write(md)
        except OSError:
            pass


if __name__ == "__main__":
    main()
