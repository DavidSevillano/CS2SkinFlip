package com.burixer85.cs2skinflip.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.burixer85.cs2skinflip.R
import com.burixer85.cs2skinflip.core.domain.model.SkinRarity
import com.burixer85.cs2skinflip.core.ui.theme.AccentGreen
import com.burixer85.cs2skinflip.core.ui.theme.AccentRed
import com.burixer85.cs2skinflip.core.ui.theme.PremiumGold
import com.burixer85.cs2skinflip.core.ui.theme.PremiumGoldDark
import com.burixer85.cs2skinflip.core.ui.theme.RarityClassified
import com.burixer85.cs2skinflip.core.ui.theme.RarityConsumer
import com.burixer85.cs2skinflip.core.ui.theme.RarityContraband
import com.burixer85.cs2skinflip.core.ui.theme.RarityCovert
import com.burixer85.cs2skinflip.core.ui.theme.RarityIndustrial
import com.burixer85.cs2skinflip.core.ui.theme.RarityKnife
import com.burixer85.cs2skinflip.core.ui.theme.RarityMilSpec
import com.burixer85.cs2skinflip.core.ui.theme.RarityRestricted
import com.burixer85.cs2skinflip.core.ui.theme.SurfaceVariant
import com.burixer85.cs2skinflip.core.ui.theme.TextSecondary

@Composable
fun PriceChangeChip(change: Double, modifier: Modifier = Modifier) {
    val isPositive = change >= 0
    val color = if (isPositive) AccentGreen else AccentRed
    val sign = if (isPositive) "+" else ""
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "$sign${"%.2f".format(change)}%",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun RarityBadge(rarity: SkinRarity, modifier: Modifier = Modifier) {
    val color = rarityColor(rarity)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = stringResource(rarity.displayNameRes),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

fun rarityColor(rarity: SkinRarity): Color = when (rarity) {
    SkinRarity.CONSUMER -> RarityConsumer
    SkinRarity.INDUSTRIAL -> RarityIndustrial
    SkinRarity.MIL_SPEC -> RarityMilSpec
    SkinRarity.RESTRICTED -> RarityRestricted
    SkinRarity.CLASSIFIED -> RarityClassified
    SkinRarity.COVERT -> RarityCovert
    SkinRarity.CONTRABAND -> RarityContraband
    SkinRarity.KNIFE -> RarityKnife
}

@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "translate"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(SurfaceVariant, SurfaceVariant.copy(0.4f), SurfaceVariant),
                    start = Offset(translateAnim - 300, 0f),
                    end = Offset(translateAnim, 0f)
                )
            )
    )
}

@Composable
fun SkinListSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(8.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBox(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp))
                    Spacer(Modifier.height(6.dp))
                    SkeletonBox(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp))
                }
                Spacer(Modifier.width(8.dp))
                SkeletonBox(modifier = Modifier.width(60.dp).height(20.dp))
            }
        }
    }
}

@Composable
fun PremiumBanner(
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(PremiumGoldDark.copy(0.3f), PremiumGold.copy(0.15f)))
                )
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = PremiumGold,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.upgrade_to_premium),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = PremiumGold
                    )
                    Text(
                        text = stringResource(R.string.premium_description),
                        fontSize = 12.sp,
                        color = PremiumGold.copy(alpha = 0.7f)
                    )
                }
                Button(
                    onClick = onUpgradeClick,
                    colors = ButtonDefaults.buttonColors(containerColor = PremiumGold),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.premium_price_monthly), color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun Modifier.shimmerEffect(): Modifier = composed {
    val shimmer = rememberInfiniteTransition(label = "s")
    val alpha by shimmer.animateFloat(
        0.2f, 0.6f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "a"
    )
    background(SurfaceVariant.copy(alpha = alpha))
}
