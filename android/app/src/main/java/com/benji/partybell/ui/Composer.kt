package com.benji.partybell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benji.partybell.ChatChannel
import com.benji.partybell.PartyState

@Composable
fun Composer(channel: String, enabled: Boolean, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val message = PartyState.clean(text)
    val problem = PartyState.problem(message)
    val canSend = enabled && message.isNotEmpty() && problem == null
    val shape = RoundedCornerShape(22.dp)

    Column(modifier) {
        if (problem != null) {
            Text(
                problem,
                color = AppColors.red,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                enabled = enabled,
                maxLines = 5,
                textStyle = TextStyle(color = AppColors.text, fontSize = 15.sp, lineHeight = 21.sp, fontFamily = Jakarta),
                cursorBrush = SolidColor(AppColors.sage),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .weight(1f)
                    .background(AppColors.card, shape)
                    .border(1.dp, AppColors.line, shape)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                decorationBox = { field ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                when {
                                    !enabled -> "Not connected"
                                    channel == ChatChannel.ALLIANCE -> "Message alliance"
                                    else -> "Message party"
                                },
                                color = AppColors.muted,
                                fontSize = 15.sp,
                            )
                        }
                        field()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (canSend) AppColors.sage else AppColors.line)
                    .clickable(enabled = canSend) {
                        PartyState.send(channel, message)
                        text = ""
                    },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) AppColors.onSage else AppColors.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
