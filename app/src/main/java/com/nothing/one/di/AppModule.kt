package com.nothing.one.di

import android.content.Context
import androidx.room.Room
import com.nothing.one.ai.LocalAiClient
import com.nothing.one.ai.OnDeviceAiClient
import com.nothing.one.data.local.ChatMessageDao
import com.nothing.one.data.local.EntryDao
import com.nothing.one.data.local.JournalDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): JournalDatabase =
        Room.databaseBuilder(context, JournalDatabase::class.java, "journal.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideEntryDao(db: JournalDatabase): EntryDao = db.entryDao()

    @Provides
    fun provideFocusSessionDao(
        db: JournalDatabase,
    ): com.nothing.one.feature.focus.data.FocusSessionDao = db.focusSessionDao()

    @Provides
    fun provideChatMessageDao(db: JournalDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides
    @Singleton
    fun provideLocalAiClient(impl: OnDeviceAiClient): LocalAiClient = impl
}
