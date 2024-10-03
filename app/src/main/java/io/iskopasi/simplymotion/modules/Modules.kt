package io.iskopasi.simplymotion.modules

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.iskopasi.simplymotion.models.GeneralRepo
import io.iskopasi.simplymotion.room.MDDao
import io.iskopasi.simplymotion.room.MDDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class HiltModules {
    @Provides
    @Singleton
    fun provideRoom(
        @ApplicationContext context: Context,
    ): MDDatabase {
        return Room.databaseBuilder(
            context,
            MDDatabase::class.java, "md_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideDao(db: MDDatabase): MDDao {
        return db.mdDao()
    }

    @Provides
    @Singleton
    fun provideRepo(dao: MDDao): GeneralRepo {
        return GeneralRepo(dao)
    }
}