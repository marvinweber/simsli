package net.marvinweber.simsli.di

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import net.marvinweber.simsli.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Realtime)
            install(Auth) {
                // Implicit, not the default PKCE: magic-link tokens then travel in the
                // redirect fragment and can't be orphaned from a lost code verifier.
                flowType = FlowType.IMPLICIT
                // The redirect URL is "$scheme://$host" — a URI host can never contain
                // a slash, so this must stay "auth" (the "/callback" idea would silently
                // never match in handleDeeplinks). Must match the intent filter in
                // AndroidManifest.xml and site_url / additional_redirect_urls in
                // supabase/config.toml.
                scheme = "simsli"
                host = "auth"
            }
        }
    }
}
