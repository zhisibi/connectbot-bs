package com.sbssh.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.sbssh.data.db.AppDatabase
import com.sbssh.data.db.VpsDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SbsshDatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideVpsDao(database: AppDatabase): VpsDao = database.vpsDao()
}
