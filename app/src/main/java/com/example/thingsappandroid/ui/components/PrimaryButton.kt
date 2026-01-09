package com.example.thingsappandroid.ui.components

import android.R.attr.y
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.R
import com.example.thingsappandroid.ui.theme.PrimaryGreen
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = PrimaryGreen,
    contentColor: Color = Color.White,
    border: BorderStroke? = null,
    icon: Int? = null,
    iconPainter: Painter? = null,
    contentDescription: String? = null,
    iconTint: Color = Color.Unspecified,
    showBox: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge
) {
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val buttonOffsetY by animateDpAsState(
        targetValue = if (isPressed && enabled) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 100),
        label = "buttonOffset"
    )

    val shadowOffsetY by animateDpAsState(
        targetValue = if (isPressed && enabled) 0.dp else 2.dp,
        animationSpec = tween(durationMillis = 100),
        label = "shadowOffset"
    )

    val shadowAlpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0f else 0.12f,
        animationSpec = tween(durationMillis = 100),
        label = "shadowAlpha"
    )

    Box(modifier = modifier) {
        // Shadow box
       if(showBox)
           Box(
               modifier = Modifier
                   .fillMaxWidth()
                   .height(56.dp)
                   .offset(y = shadowOffsetY)
                   .background(
                       color = Color(0xFF000000).copy(alpha = shadowAlpha),
                       shape = RoundedCornerShape(12.dp)
                   )
           )

        // Button container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .offset(y = buttonOffsetY)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    color = if (enabled) containerColor else containerColor.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                )
                .then(
                    if (border != null) {
                        Modifier.border(
                            border = border,
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable(
                    enabled = enabled,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    scope.launch {
                        isPressed = true
                        delay(150)
                        isPressed = false
                        onClick()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                val painter = iconPainter ?: icon?.let { painterResource(id = it) }

                if (painter != null) {
                    Icon(
                        painter = painter,
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.CenterStart),
                        tint = iconTint
                    )
                }

                Text(
                    text = text,
                    style = textStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) contentColor else contentColor.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun EvaluationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    PrimaryButton(
        text = stringResource(id = R.string.login_title),
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyLarge
    )
}

@Preview(showBackground = true)
@Composable
fun PrimaryButtonPreview() {
    ThingsAppAndroidTheme {
        PrimaryButton(
            text = "Sign In",
            onClick = {},
            showBox = true
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PrimaryButtonWithIconPreview() {
    ThingsAppAndroidTheme {
        PrimaryButton(
            text = "Continue with Google",
            onClick = {},
            icon = android.R.drawable.ic_menu_camera,
            showBox = true
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EvaluationButtonPreview() {
    ThingsAppAndroidTheme {
        EvaluationButton(
            onClick = {},
        )
    }
}