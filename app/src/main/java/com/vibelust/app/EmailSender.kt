package com.vibelust.app

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object EmailSender {
    private const val SMTP_HOST = "smtp.gmail.com"
    private const val SMTP_PORT = 465
    private const val SENDER_EMAIL = "vibelust.music@gmail.com"
    // Clean spaces out of Gmail app password "qmqy errn pqvr bots"
    private const val SENDER_PASS = "qmqyerrnpqvrbots"

    suspend fun sendWelcomeAndAdminNotification(userEmail: String, displayName: String): Boolean {
        return withContext(Dispatchers.IO) {
            var success = false
            var socket: SSLSocket? = null
            var writer: PrintWriter? = null
            var reader: BufferedReader? = null

            try {
                Log.i("EmailSender", "Initializing highly secure SMTP SSL session to $SMTP_HOST:$SMTP_PORT...")
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                socket = factory.createSocket(SMTP_HOST, SMTP_PORT) as SSLSocket
                socket.soTimeout = 12000
                socket.startHandshake()

                reader = BufferedReader(InputStreamReader(socket.inputStream, "UTF-8"))
                writer = PrintWriter(OutputStreamWriter(socket.outputStream, "UTF-8"), true)

                fun readResponse(): String {
                    val sb = StringBuilder()
                    var line = reader.readLine()
                    if (line != null) {
                        sb.append(line).append("\n")
                        // SMTP multi-line response lines have '-' as 4th char
                        while (line != null && line.length > 3 && line[3] == '-') {
                            line = reader.readLine()
                            sb.append(line).append("\n")
                        }
                    }
                    val resp = sb.toString()
                    Log.d("EmailSender", "SMTP SERVER: ${resp.trim()}")
                    return resp
                }

                // Read welcome greeting
                readResponse()

                // 1. EHLO
                writer.println("EHLO $SMTP_HOST")
                readResponse()

                // 2. AUTH LOGIN
                writer.println("AUTH LOGIN")
                readResponse()

                // 3. Send Base64 Username
                val b64User = Base64.encodeToString(SENDER_EMAIL.toByteArray(), Base64.NO_WRAP)
                writer.println(b64User)
                readResponse()

                // 4. Send Base64 App Password
                val b64Pass = Base64.encodeToString(SENDER_PASS.toByteArray(), Base64.NO_WRAP)
                writer.println(b64Pass)
                var authResp = readResponse()
                if (!authResp.contains("235")) {
                    Log.e("EmailSender", "Authentication rejected by Google SMTP server: $authResp")
                    return@withContext false
                }

                // 5. Send Welcome email to user
                Log.i("EmailSender", "Dispatching welcome notification to new user: $userEmail")
                writer.println("MAIL FROM:<$SENDER_EMAIL>")
                readResponse()

                writer.println("RCPT TO:<$userEmail>")
                readResponse()

                writer.println("DATA")
                readResponse()

                writer.println("From: Vibe Lust <$SENDER_EMAIL>")
                writer.println("To: $userEmail")
                writer.println("Subject: Vibe Lust - Welcome $displayName!")
                writer.println("Content-Type: text/html; charset=utf-8")
                writer.println()
                writer.println("""
                    <html>
                    <body style="font-family: sans-serif; background-color: #07070b; color: #cbd5e1; padding: 20px;">
                        <h2 style="color: #8b5cf6;">✧ Welcome to Vibe Lust Wallpapers ✧</h2>
                        <p>Hello <strong>$displayName</strong>,</p>
                        <p>Your gateway to stunning high-performance cosmic live loops and personalized video wallpapers is now active!</p>
                        <p>Feel free to upload your own custom looping video wallpapers, set, trim and enjoy live wallpapers smoothly on your launcher.</p>
                        <br/>
                        <p style="font-size: 11px; color: #fbbf24;">Best regards,<br/>The Vibe Lust Engineering Team</p>
                    </body>
                    </html>
                """.trimIndent())
                writer.println(".")
                readResponse()

                // 6. Send Alert email to admins (shubhamraj.std@gmail.com and vibelust.music@gmail.com)
                val admins = listOf("shubhamraj.std@gmail.com", "vibelust.music@gmail.com")
                for (admin in admins) {
                    Log.i("EmailSender", "Dispatching alert notification to admin: $admin")
                    writer.println("MAIL FROM:<$SENDER_EMAIL>")
                    readResponse()

                    writer.println("RCPT TO:<$admin>")
                    readResponse()

                    writer.println("DATA")
                    readResponse()

                    writer.println("From: Vibe Lust System <$SENDER_EMAIL>")
                    writer.println("To: $admin")
                    writer.println("Subject: Vibe Lust System - New User Signup Alert")
                    writer.println("Content-Type: text/html; charset=utf-8")
                    writer.println()
                    writer.println("""
                        <html>
                        <body style="font-family: sans-serif; background-color: #07070b; color: #cbd5e1; padding: 20px;">
                            <h2 style="color: #fbbf24;">✦ New User Registered ✦</h2>
                            <p>Hello Admin,</p>
                            <p>A new user has successfully logged in and registered on Vibe Lust Wallpapers.</p>
                            <hr style="border: 1px solid #131322;"/>
                            <p><strong>Display Name:</strong> $displayName</p>
                            <p><strong>Email Address:</strong> $userEmail</p>
                            <p><strong>Registration Time:</strong> ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}</p>
                            <hr style="border: 1px solid #131322;"/>
                            <p style="font-size: 11px; color: #cbd5e1;">Logged, secured, and synced.</p>
                        </body>
                        </html>
                    """.trimIndent())
                    writer.println(".")
                    readResponse()
                }

                // 7. QUIT
                writer.println("QUIT")
                readResponse()
                success = true

            } catch (e: Exception) {
                Log.e("EmailSender", "Failed to complete secure SMTP pipeline", e)
            } finally {
                try { writer?.close() } catch (e: Exception) {}
                try { reader?.close() } catch (e: Exception) {}
                try { socket?.close() } catch (e: Exception) {}
            }
            success
        }
    }
}
