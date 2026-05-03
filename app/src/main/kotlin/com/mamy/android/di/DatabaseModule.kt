package com.mamy.android.di

import android.content.Context
import androidx.room.Room
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.BriefingDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.MeetingAttendeeDao
import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.data.secrets.SecretsVault
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        secretsVault: SecretsVault,
    ): MamYDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = secretsVault.getOrCreateDbPassphrase()
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(context, MamYDatabase::class.java, MamYDatabase.DB_NAME)
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun providePersonDao(db: MamYDatabase): PersonDao = db.personDao()
    @Provides fun provideNoteDao(db: MamYDatabase): NoteDao = db.noteDao()
    @Provides fun provideActionDao(db: MamYDatabase): ActionDao = db.actionDao()
    @Provides fun providePromiseDao(db: MamYDatabase): PromiseDao = db.promiseDao()
    @Provides fun provideFlagDao(db: MamYDatabase): FlagDao = db.flagDao()
    @Provides fun provideMeetingDao(db: MamYDatabase): MeetingDao = db.meetingDao()
    @Provides fun provideMeetingAttendeeDao(db: MamYDatabase): MeetingAttendeeDao = db.meetingAttendeeDao()
    @Provides fun provideBriefingDao(db: MamYDatabase): BriefingDao = db.briefingDao()
}
