package com.fourpixell.facerecognition.homesection

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fourpixell.facerecognition.R

@Composable
fun HomeScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToVerify: () -> Unit
){
    Column(
        modifier = Modifier.fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img1),
            contentDescription = "Home Image",
            modifier = Modifier.fillMaxWidth(1f)
        )

        Spacer( modifier = Modifier.height(30.dp))

        HomeActionButtons(
            text = "REGISTER FACE",
            onClick = onNavigateToRegister
        )

        Spacer( modifier = Modifier.height(30.dp))

        HomeActionButtons(
            text = "VERIFY FACE",
            onClick = onNavigateToVerify
        )


    }
}

@Composable
fun HomeActionButtons(
    text: String,
    onClick: () -> Unit
){
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(20),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = Modifier.fillMaxWidth().height(56.dp)

    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }

}

