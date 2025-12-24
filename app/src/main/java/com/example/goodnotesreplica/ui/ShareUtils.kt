package com.example.goodnotesreplica.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 파일을 외부 앱과 공유합니다.
 *
 * @param context 컨텍스트
 * @param file 공유할 파일
 * @param mimeType 파일의 MIME 타입
 * @param title 공유 시트 제목
 */
fun shareFile(context: Context, file: File, mimeType: String, title: String) {
    val uri = file.toShareUri(context)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

/**
 * 파일을 외부 앱으로 엽니다.
 *
 * @param context 컨텍스트
 * @param file 열 파일
 * @param mimeType 파일의 MIME 타입
 */
fun openFile(context: Context, file: File, mimeType: String) {
    val uri = file.toShareUri(context)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}

private fun File.toShareUri(context: Context): Uri {
    val authority = "${context.packageName}.fileprovider"
    return FileProvider.getUriForFile(context, authority, this)
}
