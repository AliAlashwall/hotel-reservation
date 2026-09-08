# R8 rules for the release build.
#
# Retrofit, Room, Hilt and Coil all ship their own consumer rules, so what is left here
# is the two things R8 cannot infer from this project on its own.

# --- kotlinx.serialization -----------------------------------------------------------
# The generated serializer for a class is found reflectively by name, so R8 has no edge
# to it from any call site and will remove it. Without this, every DTO and every
# navigation route fails to parse in a minified build.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Network DTOs and navigation routes, both of which are only ever reached through a
# serializer.
-keep,includedescriptorclasses class com.alialashwal.hotelreservation.core.network.dto.**{ *; }
-keep,includedescriptorclasses class com.alialashwal.hotelreservation.navigation.**{ *; }
-keepclassmembers class com.alialashwal.hotelreservation.core.network.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.alialashwal.hotelreservation.navigation.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Generic signatures --------------------------------------------------------------
# Retrofit reads the return type of a suspend function reflectively, and the app's
# response types are generic (ListEnvelope<HotelListItemDto>). Erasing the signature
# leaves it unable to pick a converter.
-keepattributes Signature, Exceptions

# --- Crash readability ---------------------------------------------------------------
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
