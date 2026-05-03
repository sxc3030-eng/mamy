package com.mamy.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mamy.android.data.db.converter.Converters
import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.BriefingDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.MeetingAttendeeDao
import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.data.db.entity.ActionEntity
import com.mamy.android.data.db.entity.BriefingEntity
import com.mamy.android.data.db.entity.FlagEntity
import com.mamy.android.data.db.entity.MeetingAttendeeEntity
import com.mamy.android.data.db.entity.MeetingEntity
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.db.entity.PromiseEntity

@Database(
    entities = [
        PersonEntity::class,
        NoteEntity::class,
        ActionEntity::class,
        PromiseEntity::class,
        FlagEntity::class,
        MeetingEntity::class,
        MeetingAttendeeEntity::class,
        BriefingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MamYDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun noteDao(): NoteDao
    abstract fun actionDao(): ActionDao
    abstract fun promiseDao(): PromiseDao
    abstract fun flagDao(): FlagDao
    abstract fun meetingDao(): MeetingDao
    abstract fun meetingAttendeeDao(): MeetingAttendeeDao
    abstract fun briefingDao(): BriefingDao

    companion object {
        const val DB_NAME = "mamy.db"
    }
}
