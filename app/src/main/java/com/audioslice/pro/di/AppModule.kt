package com.audioslice.pro.di

import android.content.Context
import com.audioslice.pro.audioeditor.AudioEditor
import com.audioslice.pro.processor.AudioProcessor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAudioProcessor(@ApplicationContext context: Context): AudioProcessor {
        return AudioProcessor(context, Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun provideAudioEditor(@ApplicationContext context: Context): AudioEditor {
        return AudioEditor(context)
    }
}
