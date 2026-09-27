package com.nothing.one.feature.music.di

import android.content.Context
import com.nothing.one.feature.music.data.local.AppDatabase
import com.nothing.one.feature.music.data.local.dao.FavoriteDao
import com.nothing.one.feature.music.data.local.dao.PlaylistDao
import com.nothing.one.feature.music.data.local.dao.ScannedFolderDao
import com.nothing.one.feature.music.data.local.dao.SearchHistoryDao
import com.nothing.one.feature.music.data.repository.MusicRepositoryImpl
import com.nothing.one.feature.music.data.scanner.AudioScanner
import com.nothing.one.feature.music.domain.repository.MusicRepository
import com.nothing.one.feature.music.util.SettingsManager
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideScannedFolderDao(db: AppDatabase): ScannedFolderDao = db.scannedFolderDao()

    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideSearchHistoryDao(db: AppDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides
    @Singleton
    fun provideSettingsManager(@ApplicationContext context: Context): SettingsManager =
        SettingsManager(context)

    @Provides
    @Singleton
    fun provideAudioScanner(@ApplicationContext context: Context): AudioScanner =
        AudioScanner(context)

    @Provides
    @Singleton
    fun provideMusicRepository(
        @ApplicationContext context: Context,
        scanner: AudioScanner,
        playlistDao: PlaylistDao,
        scannedFolderDao: ScannedFolderDao,
        favoriteDao: FavoriteDao,
    ): MusicRepository = MusicRepositoryImpl(context, scanner, playlistDao, scannedFolderDao, favoriteDao)
}