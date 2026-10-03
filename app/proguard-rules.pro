# Smart Island Proguard Rules
#
# Scope note: this file intentionally contains ONLY rules for members that
# Smart Island reaches via reflection, plus the library consumer rules that
# AndroidX ships itself.
#
# The two blanket rules that used to live here were removed:
#
#   -keep class androidx.compose.** { *; }
#   -keep class androidx.datastore.preferences.core.** { *; }
#
# Neither library needs an app-level keep. Compose and DataStore both ship
# comprehensive consumer ProGuard/R8 rules, so the framework already knows how
# to shrink them. Keeping every member of both packages from shrinking and
# repacking was inflating the release APK and defeating R8 class merging for
# the two largest dependency trees in the app.
#
# If a release build ever fails with a missing-class or missing-method error
# from Compose or DataStore, that is a genuine signal to investigate, not
# something to silence by re-adding a wildcard keep.

# ── Platform reflection targets used in SmartIslandOverlayService ──
# setupTouchableRegion() builds a dynamic Proxy for the hidden
# ViewTreeObserver.OnComputeInternalInsetsListener so the overlay window can
# pass touches through to whatever is underneath the pill.
-keep class android.view.ViewTreeObserver$OnComputeInternalInsetsListener { *; }
-keep class android.view.ViewTreeObserver {
    public void addOnComputeInternalInsetsListener(android.view.ViewTreeObserver$OnComputeInternalInsetsListener);
}
-keep class android.view.View$InternalInsetsInfo {
    public void setTouchableInsets(int);
    *** mTouchableRegion;
    *** touchableRegion;
}

# Keep ActivityOptions and setLaunchWindowingMode
-keep class android.app.ActivityOptions {
    public void setLaunchWindowingMode(int);
    public void setPendingIntentBackgroundActivityStartMode(int);
}

# ── MediaController reflection used in MusicExpanded ──
# Keep repeat mode methods accessed via reflection
-keep class android.media.session.MediaController {
    public int getRepeatMode();
}
-keep class android.media.session.MediaController$TransportControls {
    public void setRepeatMode(int);
}
# Keep Rating.isHearted() accessed via reflection
-keep class android.media.Rating {
    public boolean isHearted();
}
# Keep MediaMetadata.getRating() used to obtain Rating object
-keep class android.media.MediaMetadata {
    public android.media.Rating getRating(java.lang.String);
}
# Keep PlaybackState custom actions and extras
-keep class android.media.session.PlaybackState {
    public java.util.List getCustomActions();
}
-keep class android.media.session.PlaybackState$CustomAction {
    public java.lang.String getAction();
    public java.lang.CharSequence getName();
}
