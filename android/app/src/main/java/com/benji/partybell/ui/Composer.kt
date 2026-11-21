package com.benji.partybell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.benji.partybell.Reasons
import com.benji.partybell.PartyState
import com.benji.partybell.QUICK_REPLIES

private val ChipShape = RoundedCornerShape(50)

@Composable
fun Composer(placeholder: String, channel: String, enabled: Boolean, modifier: Modifier = Modifier, to: String = "") {
    var text by rememberSaveable(channel, to) { mutableStateOf("") }
    val message = PartyState.clean(text)
    val problem = PartyState.problem(message, channel, to)
    val canSend = enabled && message.isNotEmpty() && problem == null
    val quick = if (enabled && text.isEmpty()) QUICK_REPLIES.filter { PartyState.problem(it, channel, to) == null } else emptyList()
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
        if (quick.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
            ) {
                quick.forEach { reply ->
                    Text(
                        reply,
                        color = AppColors.text,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(ChipShape)
                            .background(AppColors.card)
                            .border(1.dp, AppColors.line, ChipShape)
                            .clickable { PartyState.send(channel, reply, to) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                enabled = enabled,
                maxLines = 5,
                textStyle = TextStyle(color = AppColors.text, fontSize = 15.sp, lineHeight = 21.sp, fontFamily = Jakarta),
                cursorBrush = SolidColor(AppColors.accent),
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
                                if (enabled) placeholder else Reasons.NOT_CONNECTED,
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
                    .background(if (canSend) AppColors.accent else AppColors.line)
                    .clickable(enabled = canSend) {
                        PartyState.send(channel, message, to)
                        text = ""
                    },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) AppColors.onAccent else AppColors.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
