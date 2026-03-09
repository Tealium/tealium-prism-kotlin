# Module Tealium Prism

A library to integrate the Tealium CDP into your Android apps.

## Requirements
Minimum OS versions:
- Android: 23.0+

## Installation

### Maven/Gradle
tealium-prism is currently available via our Maven repository. To install:

In your Android project, add the following maven repository

```kotlin
dependencyResolutionManagement {
    repositories {
        // .. other repos
        maven {
            url = URI("https://maven.tealiumiq.com/android/releases/")
        }
    }
}
```
Then add your required tealium-prism dependencies

```kotlin
implementation(platform("com.tealium.prism:prism-bom:0.4.0"))
implementation("com.tealium.prism:prism-core")
implementation("com.tealium.prism:prism-lifecycle")
implementation("com.tealium.prism:prism-moments-api") 
```

And re-sync your Gradle projects

## Usage

To start using the library:

1. Import the necessary modules
2. Initialize a `Tealium` instance
3. Start tracking events.

```kotlin
import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.TealiumConfig

val config = TealiumConfig.Builder(
   accountName = "my_account",
   profileName = "my_profile",
   environment = Environment.PROD,
   modules = listOf(
        Modules.appData(),
        Modules.collect(),
        Modules.connectivityData(),
        Modules.deepLink(),
        Modules.deviceData(),
        Modules.lifecycle(),
        Modules.timeData(),
        Modules.trace(),
       )
).build()
val tealium = Tealium.create(config)
tealium.track("An Event")
```

For more advanced usage and detailed documentation, visit our [developer documentation](https://tealium.github.io/tealium-prism-kotlin/).

## License

tealium-prism is available under a commercial license. See the [LICENSE](./LICENSE) file for more info.