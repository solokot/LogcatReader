package com.dp.logcatapp.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "filters")
data class FilterInfo(
  @PrimaryKey @ColumnInfo(name = "id") val id: Long? = null,
  @ColumnInfo(name = "tag") val tag: String? = null,
  @ColumnInfo(name = "message") val message: String? = null,
  @ColumnInfo(name = "pid") val pid: Int? = null,
  @ColumnInfo(name = "tid") val tid: Int? = null,
  @ColumnInfo(name = "package_name") val packageName: String? = null,
  @ColumnInfo(name = "log_levels") val logLevels: String? = null,
  @ColumnInfo(name = "exclude") val exclude: Boolean = false,
  @ColumnInfo(name = "enabled") val enabled: Boolean = true,
  // Comma separated values of `RegexFilterType`
  @ColumnInfo(name = "regex_enabled_filter_types") val regexEnabledFilterTypes: String? = null,
)

fun FilterInfo.enableRegexFor(
  vararg types: RegexFilterType
): FilterInfo {
  return copy(
    regexEnabledFilterTypes = types.joinToString(separator = ",") { it.ordinal.toString() },
  )
}

val FilterInfo.regexFilterTypes: Set<RegexFilterType>
  get() = regexEnabledFilterTypes?.split(",")
    ?.mapNotNull { it.toIntOrNull() }
    ?.map { RegexFilterType.entries[it] }
    ?.toSet()
    .orEmpty()

enum class RegexFilterType {
  Tag,
  Message,
  PackageName,
}

@Dao
interface FilterDao {

  @Query("SELECT * FROM filters")
  fun filters(): Flow<List<FilterInfo>>

  @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
  fun insert(vararg info: FilterInfo)

  @Delete
  fun delete(vararg info: FilterInfo)

  @Update(onConflict = OnConflictStrategy.REPLACE)
  fun update(vararg info: FilterInfo)

  @Query("DELETE FROM filters")
  fun deleteAll()
}