package com.example.thingsappandroid.features.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.thingsappandroid.R
import com.example.thingsappandroid.ui.theme.PrimaryGreen
import com.example.thingsappandroid.ui.theme.SecondaryGreen
import com.example.thingsappandroid.ui.theme.TextPrimary
import com.example.thingsappandroid.ui.theme.TextSecondary
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class OnboardingPage(
    val image: Int,
    val title: String,
    val description: String
)

@Composable
fun OnboardingScreen(
    onOnboardingFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val pages = listOf(
        OnboardingPage(
            image = R.drawable.onboard,
            title = "Understand Your\nCharging Impact",
            description = "ThingsApp shows how charging your\nphone affects the climate — automatically,\nwith no setup."
        ),
        OnboardingPage(
            image = R.drawable.onboard,
            title = "Your Device Has a\nClimate Status",
            description = "Your device gets a climate status based\non how and where you charge. Cleaner\nelectricity keeps it green."
        ),
        OnboardingPage(
            image = R.drawable.onboard,
            title = "Works in the\nBackground",
            description = "ThingsApp tracks charging only, not your\napps or personal data. A better climate\nstatus can unlock rewards."
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    var isTermsAccepted by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    // Handle ViewModel effects
    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            android.util.Log.d("OnboardingScreen", "Received effect: $effect")
            when (effect) {
                is OnboardingEffect.NavigateToHome -> {
                    android.util.Log.d("OnboardingScreen", "Navigating to home...")
                    onOnboardingFinished()
                    android.util.Log.d("OnboardingScreen", "onOnboardingFinished() called")
                }
                is OnboardingEffect.ShowError -> {
                    android.util.Log.e("OnboardingScreen", "Error: ${effect.message}")
                    android.widget.Toast.makeText(
                        context,
                        effect.message,
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            // Bottom Action Area with system navigation bar padding
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                if (pagerState.currentPage == pages.lastIndex) {
                    Column {
                        // Terms Checkbox
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Checkbox(
                                checked = isTermsAccepted,
                                onCheckedChange = { isTermsAccepted = it }
                            )
                            Text(
                                text = stringResource(R.string.terms_agreement_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { viewModel.onGetStartedClicked() },
                            enabled = isTermsAccepted && !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryGreen,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Get Started",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                // Skip button should navigate to last page instead of finishing
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pages.lastIndex)
                                }
                            },
                            modifier = Modifier
                                .weight(0.35f)
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SecondaryGreen,
                                contentColor = PrimaryGreen
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp)
                        ) {
                            Text(
                                text = "Skip",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            modifier = Modifier
                                .weight(0.65f)
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryGreen,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp)
                        ) {
                            Text(
                                text = "Next",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                OnboardingPageContent(
                    page = pages[page],
                    pagerState = pagerState,
                    pageCount = pages.size
                )
            }
        }
    }
}

@Composable
fun OnboardingPageContent(
    page: OnboardingPage,
    pagerState: PagerState,
    pageCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Image Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = page.image),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxHeight()
            )
        }

        // Text Content Area
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            PageIndicator(
                pagerState = pagerState,
                pageCount = pageCount
            )
        }
    }
}

@Composable
fun PageIndicator(
    pagerState: PagerState,
    pageCount: Int
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { iteration ->
            val color = if (pagerState.currentPage == iteration) PrimaryGreen else SecondaryGreen
            val size = 8.dp

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clip(CircleShape)
                    .background(color)
                    .size(size)
            )
        }
    }
}