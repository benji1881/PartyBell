package com.benji.partybell

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

sealed interface ScanResult {
    data class Paired(val pairing: Pairing) : ScanResult
    data object NotPartyBell : ScanResult
    data object Failed : ScanResult
}

object PairingScanner {
    fun scan(context: Context, onResult: (ScanResult) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                val pairing = Pairing.parse(barcode.rawValue.orEmpty())
                onResult(if (pairing != null) ScanResult.Paired(pairing) else ScanResult.NotPartyBell)
            }
            .addOnFailureListener { onResult(ScanResult.Failed) }
    }
}
