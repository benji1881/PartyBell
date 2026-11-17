package com.benji.partybell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.benji.partybell.ChatItem
import com.benji.partybell.EventKind
import com.benji.partybell.FeedItem
import com.benji.partybell.PartyEventItem

private val AvatarSize = 56.dp
private val BodyIndent = 36.dp
private val TextIndent = 27.dp
private val BubbleText = TextStyle(
    color = AppColors.Text,
    fontSize = 15.sp,
    letterSpacing = 0.015.em,
    shadow = Shadow(AppColors.Shadow, Offset(0f, 1f), 2f),
)

@Composable
fun Feed(items: List<FeedItem>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        reverseLayout = true,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(items.asReversed()) { item ->
            when (item) {
                is ChatItem -> ChatBubble(item.name, item.job, item.role, item.text, item.channel == "alliance")
                is PartyEventItem -> when (item.kind) {
                    EventKind.Joined, EventKind.Left -> ChatBubble(item.name, item.job, item.role, item.text, false)
                    EventKind.Full, EventKind.Disbanded -> Notice(item.text)
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(name: String, job: String, role: Int, text: String, alliance: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(start = BodyIndent, end = 24.dp)) {
            Text(
                text = (if (alliance) "$name · alliance" else name).uppercase(),
                color = AppColors.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.06.em,
                maxLines = 1,
                modifier = Modifier
                    .height(22.dp)
                    .shadow(1.dp, RoundedCornerShape(11.dp))
                    .background(nameColor(name), RoundedCornerShape(11.dp))
                    .padding(start = TextIndent, end = 12.dp)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
            Text(
                text = text,
                style = BubbleText,
                modifier = Modifier
                    .offset(y = (-6).dp)
                    .heightIn(min = 40.dp)
                    .widthIn(min = 120.dp)
                    .shadow(2.dp, RoundedCornerShape(10.dp))
                    .background(AppColors.Bubble, RoundedCornerShape(10.dp))
                    .padding(start = TextIndent, top = 12.dp, end = 14.dp, bottom = 9.dp),
            )
        }
        JobAvatar(job, role, AvatarSize, Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Notice(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            color = AppColors.Text,
            style = MaterialTheme.typography.labelLarge,
            letterSpacing = 0.06.em,
            modifier = Modifier
                .background(nameColor(text), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp),
        )
    }
}
