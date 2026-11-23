package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.UserProfile
import com.example.ui.theme.CoralPulse
import com.example.ui.theme.SaffronGold
import com.example.viewmodel.UiState

@Composable
fun ProfileScreen(
    userProfileState: UiState<UserProfile?>,
    fallbackName: String,
    fallbackEmail: String,
    fallbackPhotoUrl: String?,
    favoritesCount: Int,
    playlistsCount: Int,
    offlineCount: Int,
    songsJsonUrl: String,
    adsRemoved: Boolean,
    onUpdateDisplayName: (String, (Boolean) -> Unit) -> Unit,
    onOpenServerUrlDialog: () -> Unit,
    onOpenRemoveAdsDialog: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = (userProfileState as? UiState.Success)?.data
    val displayName = profile?.displayName?.ifBlank { fallbackName } ?: fallbackName
    val email = profile?.email?.ifBlank { fallbackEmail } ?: fallbackEmail
    val photoUrl = profile?.photoUrl ?: fallbackPhotoUrl

    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember(displayName) { mutableStateOf(displayName) }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineMedium,
            color = SaffronGold,
        )

        // User Avatar & Identity Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = displayName.firstOrNull()?.uppercase() ?: "D",
                            style = MaterialTheme.typography.headlineMedium,
                            color = SaffronGold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("profile_display_name"),
                )
                Text(
                    text = email.ifBlank { "Signed in with Google" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("profile_email"),
                )
            }
        }

        // Manage Display Name
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (isEditingName) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { if (it.length <= 60) editedName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_display_name_input"),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { isEditingName = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = {
                                if (editedName.isNotBlank()) {
                                    onUpdateDisplayName(editedName.trim()) { ok ->
                                        if (ok) isEditingName = false
                                    }
                                }
                            },
                            enabled = editedName.isNotBlank(),
                            modifier = Modifier.testTag("save_display_name_button"),
                        ) {
                            Text("Save")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Badge, contentDescription = null, tint = SaffronGold)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Display Name", style = MaterialTheme.typography.labelMedium)
                            Text(displayName, style = MaterialTheme.typography.bodyLarge)
                        }
                        TextButton(
                            onClick = {
                                editedName = displayName
                                isEditingName = true
                            },
                            modifier = Modifier.testTag("edit_name_button"),
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit")
                        }
                    }
                }
            }
        }

        // Account, Library & App Settings
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Email, contentDescription = null, tint = SaffronGold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Email", style = MaterialTheme.typography.labelMedium)
                        Text(email.ifBlank { "No email" }, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = SaffronGold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Cloud & Offline Library", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "$favoritesCount favorites • $playlistsCount playlists • $offlineCount offline",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenServerUrlDialog)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = SaffronGold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Licensed songs.json Server", style = MaterialTheme.typography.labelMedium)
                        Text(
                            songsJsonUrl.ifBlank { "Tap to configure your server URL" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenRemoveAdsDialog)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = SaffronGold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Remove Ads (Paid Version)", style = MaterialTheme.typography.labelMedium)
                        Text(
                            if (adsRemoved) "Ad-Free preview enabled" else "Placeholder button — tap to preview Ad-Free",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedButton(
            onClick = { showSignOutConfirm = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("sign_out_button"),
        ) {
            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = CoralPulse)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out", color = CoralPulse)
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign Out?") },
            text = { Text("Sign out of your Desi Beats Google account?") },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOut()
                    },
                    modifier = Modifier.testTag("confirm_sign_out_button"),
                ) {
                    Text("Sign Out", color = CoralPulse)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
