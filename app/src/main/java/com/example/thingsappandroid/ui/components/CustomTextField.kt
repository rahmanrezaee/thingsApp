package com.example.thingsappandroid.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.ui.theme.BorderGray
import com.example.thingsappandroid.ui.theme.ErrorRed
import com.example.thingsappandroid.ui.theme.TextFieldBackground
import com.example.thingsappandroid.ui.theme.TextPrimary
import com.example.thingsappandroid.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge // Added textStyle parameter
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Only render label if it's not empty
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { 
                if (placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder, 
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    ) 
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            isError = isError,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = TextFieldBackground,
                unfocusedContainerColor = TextFieldBackground,
                disabledContainerColor = TextFieldBackground,
                errorContainerColor = TextFieldBackground,
                unfocusedBorderColor = BorderGray,
                focusedBorderColor = TextPrimary,
                errorBorderColor = ErrorRed,
                cursorColor = TextPrimary,
                errorCursorColor = ErrorRed
            ),
            singleLine = true,
            textStyle = textStyle // Use the passed textStyle
        )
        if (isError && errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.labelSmall,
                color = ErrorRed
            )
        }
    }
}