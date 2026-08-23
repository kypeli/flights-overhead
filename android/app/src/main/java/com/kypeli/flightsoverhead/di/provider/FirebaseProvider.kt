package com.kypeli.flightsoverhead.di.provider

import com.google.firebase.firestore.FirebaseFirestore
import com.kypeli.flightsoverhead.di.scope.ViewModelScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(ViewModelScope::class)
@BindingContainer
object FirebaseProvider {
    @Provides
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}
