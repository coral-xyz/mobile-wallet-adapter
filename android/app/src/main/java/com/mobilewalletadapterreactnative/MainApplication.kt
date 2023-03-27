package com.mobilewalletadapterreactnative

import android.app.Application
import android.content.Context
import android.content.res.Resources
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactInstanceManager
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.config.ReactFeatureFlags
import com.facebook.soloader.SoLoader
import java.lang.reflect.InvocationTargetException

class MainApplication : Application(), ReactApplication {

  private val mReactNativeHost: ReactNativeHost = object : ReactNativeHost(this) {
    override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG
    override protected fun getJSMainModuleName(): String? = "index"
//    override protected isNewArchEnabled(): Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
//    override protected isHermesEnabled(): Boolean = BuildConfig.IS_HERMES_ENABLED
    override protected fun getPackages() = PackageList(this).getPackages().apply {
      // Packages that cannot be autolinked yet can be added manually here, for example:
      add(MobileWalletAdapterReactNativePackage())
    }
  }

  override fun getReactNativeHost(): ReactNativeHost = mReactNativeHost

  override fun onCreate() {
    super.onCreate()
    // If you opted-in for the New Architecture, we enable the TurboModule system
    ReactFeatureFlags.useTurboModules = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
    SoLoader.init(this,  /* native exopackage */ false)
    initializeFlipper(this, getReactNativeHost().getReactInstanceManager());
  }

  companion object {
    /**
     * Loads Flipper in React Native templates. Call this in the onCreate method with something like
     * initializeFlipper(this, getReactNativeHost().getReactInstanceManager());
     *
     * @param context
     * @param reactInstanceManager
     */
    private fun initializeFlipper(
      context: Context, reactInstanceManager: ReactInstanceManager
    ) {
      if (BuildConfig.DEBUG) {
        try {
          /* We use reflection here to pick up the class that initializes Flipper,
          since Flipper library is not available in release mode */
          val aClass = Class.forName("com.mobilewalletadapterreactnative.ReactNativeFlipper")
          aClass
            .getMethod(
              "initializeFlipper",
              Context::class.java,
              ReactInstanceManager::class.java
            )
            .invoke(null, context, reactInstanceManager)
        } catch (e: ClassNotFoundException) {
          e.printStackTrace()
        } catch (e: NoSuchMethodException) {
          e.printStackTrace()
        } catch (e: IllegalAccessException) {
          e.printStackTrace()
        } catch (e: InvocationTargetException) {
          e.printStackTrace()
        }
      }
    }
  }
}
