# kotlinx.serialization and OkHttp ship their own consumer rules.
# Keep the API data classes' generated serializers as well (R8 full mode).
-keepclassmembers @kotlinx.serialization.Serializable class io.github.kubatdavid.food.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
