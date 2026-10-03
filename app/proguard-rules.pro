# Project-specific R8 rules. Libraries used here ship their own consumer rules.

# Type-safe navigation routes are looked up through their serializers.
-keep @kotlinx.serialization.Serializable class online.draran.billing.navigation.** { *; }
-keepclassmembers class online.draran.billing.navigation.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
