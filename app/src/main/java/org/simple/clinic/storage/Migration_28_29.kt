package org.simple.clinic.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("ClassName")
class Migration_28_29 : Migration(28, 29) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.execSQL("""
      UPDATE "Facility" SET "syncStatus" = 'PENDING' WHERE "syncStatus" = 'IN_FLIGHT'
    """)
  }
}
