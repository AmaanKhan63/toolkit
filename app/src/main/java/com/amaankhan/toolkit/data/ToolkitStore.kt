package com.amaankhan.toolkit.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.emptyPreferences

private val Context.preferences by preferencesDataStore("toolkit_preferences")
enum class ThemeChoice(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }
enum class Palette(val label: String, val description: String) {
    PLATINUM("Platinum", "Graphite, porcelain and silver"),
    CHAMPAGNE("Champagne", "Warm charcoal, ivory and gold"),
    JADE("Jade", "Mineral green and soft jade")
}
data class Preferences(
    val theme: ThemeChoice = ThemeChoice.SYSTEM, val favorites: Set<String> = emptySet(), val recent: List<String> = emptyList(),
    val palette: Palette = Palette.PLATINUM, val displayName: String = "", val workspace: String = "",
)

class ToolkitPreferences(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val favoritesKey = stringSetPreferencesKey("favorites")
    private val recentKey = stringPreferencesKey("recent_tools")
    private val paletteKey = stringPreferencesKey("palette")
    private val displayNameKey = stringPreferencesKey("display_name")
    private val workspaceKey = stringPreferencesKey("workspace")
    val values = context.preferences.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map {
        Preferences(ThemeChoice.entries.firstOrNull { theme -> theme.name == it[themeKey] } ?: ThemeChoice.SYSTEM,
            it[favoritesKey] ?: emptySet(), it[recentKey]?.split(',')?.filter(String::isNotBlank) ?: emptyList(),
            Palette.entries.firstOrNull { palette -> palette.name == it[paletteKey] } ?: Palette.PLATINUM,
            it[displayNameKey].orEmpty(), it[workspaceKey].orEmpty())
    }
    suspend fun setTheme(theme: ThemeChoice) { context.preferences.edit { it[themeKey] = theme.name } }
    suspend fun setPalette(palette: Palette) { context.preferences.edit { it[paletteKey] = palette.name } }
    suspend fun setProfile(name: String, workspace: String) { context.preferences.edit {
        it[displayNameKey] = name.trim().take(60); it[workspaceKey] = workspace.trim().take(60)
    } }
    suspend fun toggleFavorite(id: String) { context.preferences.edit { prefs ->
        val current = prefs[favoritesKey].orEmpty()
        prefs[favoritesKey] = if (id in current) current - id else current + id
    } }
    suspend fun useTool(id: String) { context.preferences.edit {
        val prior = it[recentKey]?.split(',').orEmpty().filter { old -> old.isNotBlank() && old != id }
        it[recentKey] = (listOf(id) + prior).take(4).joinToString(",")
    } }
    suspend fun clearRecents() { context.preferences.edit { it.remove(recentKey) } }
}

@Entity(tableName = "exports")
data class ExportedDocument(@PrimaryKey val uri: String, val name: String, val pages: Int, val bytes: Long, val createdAt: Long,
    @androidx.room.ColumnInfo(defaultValue = "'application/pdf'") val mime: String = "application/pdf")
@Dao
interface ExportDao {
    @Query("SELECT * FROM exports ORDER BY createdAt DESC") fun observe(): kotlinx.coroutines.flow.Flow<List<ExportedDocument>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(document: ExportedDocument)
    @Query("DELETE FROM exports WHERE uri = :uri") suspend fun remove(uri: String)
    @Query("DELETE FROM exports") suspend fun clear()
}
@Database(entities = [ExportedDocument::class], version = 2, exportSchema = true)
abstract class ToolkitDatabase : RoomDatabase() { abstract fun exports(): ExportDao }

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exports ADD COLUMN mime TEXT NOT NULL DEFAULT 'application/pdf'")
    }
}
