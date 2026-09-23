package com.jarvis.companion

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.media.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt
import javax.net.ssl.*

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var pairStatus: TextView
    private lateinit var pairCode: EditText
    private lateinit var pairPanel: View
    private lateinit var voicePanel: View
    private lateinit var orb: JarvisOrbView
    private lateinit var transcript: TextView
    private lateinit var transcriptScroll: ScrollView
    private lateinit var endConversation: ImageButton
    private lateinit var startConversation: Button
    private lateinit var phoneControl: ImageButton
    private var ws: WebSocket? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    @Volatile private var micRunning = false
    private val prefs by lazy { getSharedPreferences("jarvis-device", MODE_PRIVATE) }
    private val client by lazy { lanClient() }
    private val serverBase: String get() = prefs.getString("server", "https://auth.kasirdigital.web.id") ?: "https://auth.kasirdigital.web.id"

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        status=findViewById(R.id.status); pairStatus=findViewById(R.id.pairStatus); pairCode=findViewById(R.id.pairCode)
        pairPanel=findViewById(R.id.pairPanel); voicePanel=findViewById(R.id.voicePanel)
        orb=findViewById(R.id.orb); transcript=findViewById(R.id.transcript); transcriptScroll=findViewById(R.id.transcriptScroll); endConversation=findViewById(R.id.endConversation)
        startConversation=findViewById(R.id.startConversation); phoneControl=findViewById(R.id.phoneControl)
        findViewById<Button>(R.id.pair).setOnClickListener { pairWithCode(pairCode.text.toString()) }
        endConversation.setOnClickListener { endVoice() }
        startConversation.setOnClickListener { connect() }
        phoneControl.setOnClickListener { showPhoneControlMenu(it) }
        if (Build.VERSION.SDK_INT>=33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        intent?.data?.getQueryParameter("code")?.let { pairCode.setText(it.uppercase()); pairWithCode(it) }
        if (intent?.data==null && prefs.getBoolean("paired", false)) { showVoice(); connect() }
    }

    private fun showVoice(){ runOnUiThread { pairPanel.visibility=View.GONE; voicePanel.visibility=View.VISIBLE; status.text=getString(R.string.connecting); orb.state="CONNECTING"; endConversation.visibility=View.VISIBLE; startConversation.visibility=View.GONE } }

    private fun showPhoneControlMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            if (Build.VERSION.SDK_INT >= 29) setForceShowIcon(true)
            menu.add(0, 1, 0, getString(R.string.enable_phone_control)).setIcon(R.drawable.ic_phone_control)
            setOnMenuItemClickListener { item ->
                if (item.itemId == 1) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    true
                } else false
            }
            show()
        }
    }
    private fun showPair(message:String){ stopMic(); runOnUiThread { voicePanel.visibility=View.GONE; pairPanel.visibility=View.VISIBLE; pairStatus.text=message } }

    private fun identity(): Triple<String,ByteArray,ByteArray> {
        var id=prefs.getString("device_id",null); var priv=prefs.getString("private",null)
        if(id==null||priv==null){ val k=Ed25519PrivateKeyParameters(SecureRandom()); id=UUID.randomUUID().toString(); priv=b64(k.encoded); prefs.edit().putString("device_id",id).putString("private",priv).apply() }
        val p=Ed25519PrivateKeyParameters(unb64(priv),0); return Triple(id,p.encoded,p.generatePublicKey().encoded)
    }
    private fun sign(data:ByteArray):String { val p=Ed25519PrivateKeyParameters(identity().second,0); val s=Ed25519Signer(); s.init(true,p); s.update(data,0,data.size); return b64(s.generateSignature()) }
    private fun verify(pub:String,data:ByteArray,sig:String):Boolean = try { val v=Ed25519Signer(); v.init(false,Ed25519PublicKeyParameters(unb64(pub),0)); v.update(data,0,data.size); v.verifySignature(unb64(sig)) } catch(_:Exception){false}

    private fun pairWithCode(rawCode:String) {
        val code=rawCode.trim().uppercase()
        if(code.length != 6){ pairStatus.text=getString(R.string.pair_code_help); return }
        pairStatus.text=getString(R.string.pairing)
        pairAgainstServer(code, "https://auth.kasirdigital.web.id")
    }

    private fun pairAgainstServer(code:String, base:String) {
        val ident=identity()
        val peer=JSONObject().put("device_id",ident.first).put("name",Build.MODEL).put("public_key",b64(ident.third))
        client.newCall(Request.Builder().url("$base/api/pairing/offer/$code").build()).enqueue(object:Callback{
            override fun onFailure(c:Call,e:java.io.IOException)=pairUi("Pairing failed: ${e.message}")
            override fun onResponse(c:Call,r:Response){ r.use { response ->
                if(!response.isSuccessful){ pairUi(if(response.code==502) "JARVIS tunnel is offline (502)" else "Pairing server error: ${response.code}"); return }
                val o=try { JSONObject(response.body?.string().orEmpty()) } catch(_:Exception){ pairUi("Invalid response from JARVIS"); return }
                val nonce=o.optString("nonce"); val serverKey=o.optString("public_key"); val serverId=o.optString("device_id")
                if(nonce.isBlank()||serverKey.isBlank()||serverId.isBlank()){pairUi("Pairing code invalid or expired");return}
                val caps=org.json.JSONArray(listOf("jarvis.command","notification","vibration","clipboard.write","open_url","app.launch","app.close","android.settings.open","android.ui.inspect","android.ui.click","android.ui.text","android.ui.scroll","android.ui.global","android.screen.lock","android.screen.wake"))
                val body=JSONObject().put("code",code).put("peer",peer).put("signature",sign("$nonce:$code".toByteArray())).put("capabilities",caps)
                val req=Request.Builder().url("$base/api/pairing/accept").post(body.toString().toRequestBody("application/json".toMediaType())).build()
                client.newCall(req).enqueue(object:Callback{
                    override fun onFailure(c:Call,e:java.io.IOException)=pairUi("Pair failed: ${e.message}")
                    override fun onResponse(c:Call,r:Response){ r.use {
                        if(!it.isSuccessful){pairUi("Pair rejected: ${it.code}");return}
                        prefs.edit().putString("server",base).putString("server_key",serverKey).putString("server_id",serverId).putBoolean("paired",true).apply()
                        showVoice(); connect()
                    }}
                })
            }}
        })
    }

    private fun connect(){
        val server=prefs.getString("server",null)?:return; val id=identity().first
        val wsBase=server.replaceFirst("https://","wss://").replaceFirst("http://","ws://")
        ws=client.newWebSocket(Request.Builder().url("$wsBase/ws/device?device_id=$id").build(),object:WebSocketListener(){
            override fun onMessage(w:WebSocket,text:String){ try {
                val m=JSONObject(text); when(m.optString("type")){
                    "challenge"->{ val ch=m.getString("challenge"); val serverKey=prefs.getString("server_key","")!!; if(!verify(serverKey,"$id:$ch".toByteArray(),m.optString("server_signature"))){ ui("Server identity verification failed"); w.close(4003,"bad server proof"); return }; w.send(JSONObject().put("type","proof").put("signature",sign(ch.toByteArray())).put("capabilities", org.json.JSONArray(listOf("jarvis.command","notification","vibration","clipboard.write","open_url","app.launch","app.close","android.settings.open","android.ui.inspect","android.ui.click","android.ui.text","android.ui.scroll","android.ui.global","android.screen.lock","android.screen.wake"))).toString()) }
                    "ready"->{ runOnUiThread { endConversation.visibility=View.VISIBLE; startConversation.visibility=View.GONE }; setVoiceState("LISTENING"); startMic() }
                    "status"->{ val st=m.optString("state").uppercase(); setVoiceState(if(st=="ACTIVE") "LISTENING" else st) }
                    "log"->{ appendTranscript(m.optString("speaker"),m.optString("text")); if(m.optString("speaker")=="jarvis") setVoiceState("LISTENING") }
                    "capability.call"->executeCapability(w,m)
                }
            } catch(_:Exception){ ui("Invalid message from JARVIS") } }
            override fun onMessage(w:WebSocket,bytes:ByteString){ setVoiceState("SPEAKING"); playAudio(bytes.toByteArray()) }
            override fun onClosing(w:WebSocket,code:Int,reason:String){ stopMic(); ws=null; if(code==4001||code==4003){ prefs.edit().putBoolean("paired",false).apply(); showPair("Pairing revoked. Enter a new Pair Code.") } else setEnded() }
            override fun onFailure(w:WebSocket,t:Throwable,r:Response?){ stopMic(); ws=null; runOnUiThread { status.text="Disconnected: ${t.message}"; orb.state="DISCONNECTED"; endConversation.visibility=View.GONE; startConversation.visibility=View.VISIBLE } }
        })
    }

    private fun startMic(){
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO),42); return }
        if(micRunning)return
        val min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(2048)
        recorder=AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,min*2)
        recorder?.startRecording(); micRunning=true
        Thread {
            val buf=ByteArray(1024)
            while(micRunning){ val n=try{recorder?.read(buf,0,buf.size)?:-1}catch(_:Exception){-1}; if(n>0){ orb.audioLevel(pcmLevel(buf,n)); ws?.send(ByteString.of(*buf.copyOf(n))) } }
        }.apply { name="JarvisPhoneMic"; isDaemon=true; start() }
    }
    private fun stopMic(){ micRunning=false; try{recorder?.stop()}catch(_:Exception){}; recorder?.release(); recorder=null }
    private fun playAudio(pcm:ByteArray){
        orb.audioLevel(pcmLevel(pcm,pcm.size))
        try {
            if(player==null){ val min=AudioTrack.getMinBufferSize(24000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096); player=AudioTrack(AudioManager.STREAM_MUSIC,24000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,min*2,AudioTrack.MODE_STREAM); player?.play() }
            player?.write(pcm,0,pcm.size)
        } catch(_:Exception){}
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){ super.onRequestPermissionsResult(requestCode,permissions,grantResults); if(requestCode==42){ if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) startMic() else ui("Microphone permission is required for Live Voice") } }

    private fun setVoiceState(s:String)=runOnUiThread { status.text=s.lowercase().replaceFirstChar { it.uppercase() }; orb.state=s }
    private val transcriptTurns = ArrayDeque<String>()
    private fun appendTranscript(speaker:String,text:String){ if(text.isBlank()) return; runOnUiThread {
        val who=if(speaker.equals("user",true)) "YOU" else "JARVIS"
        transcriptTurns.addLast("$who  $text")
        while(transcriptTurns.size > 4) transcriptTurns.removeFirst()
        transcript.text=transcriptTurns.joinToString("\n\n")
        transcriptScroll.post { transcriptScroll.fullScroll(View.FOCUS_DOWN) }
    }}
    private fun endVoice(){ stopMic(); try{player?.pause();player?.flush()}catch(_:Exception){}; ws?.close(1000,"conversation ended"); ws=null; setEnded() }
    private fun setEnded()=runOnUiThread { status.text=getString(R.string.conversation_ended); orb.state="SLEEPING"; endConversation.visibility=View.GONE; startConversation.visibility=View.VISIBLE }
    private fun pcmLevel(b:ByteArray,n:Int):Float { if(n<2)return 0f; var sum=0.0; var count=0; var i=0; while(i+1<n){ val v=((b[i+1].toInt() shl 8) or (b[i].toInt() and 255)).toShort().toInt(); sum+=v.toDouble()*v;count++;i+=2 }; if(count==0)return 0f; return (sqrt(sum/count)/3500.0).toFloat().coerceIn(0f,1f) }

    private fun executeCapability(w:WebSocket,m:JSONObject){ val cap=m.optString("capability"); val a=m.optJSONObject("args")?:JSONObject(); var ok=true; var result="done"; try { when(cap){
        "notification"->{ val nm=getSystemService(NotificationManager::class.java); val cid="jarvis"; if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel(cid,"JARVIS",NotificationManager.IMPORTANCE_DEFAULT)); nm.notify((System.currentTimeMillis()%Int.MAX_VALUE).toInt(),Notification.Builder(this,cid).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("JARVIS").setContentText(a.optString("text")).build()) }
        "vibration"->{ val v=if(Build.VERSION.SDK_INT>=31)getSystemService(VibratorManager::class.java).defaultVibrator else @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator; v.vibrate(VibrationEffect.createOneShot(a.optLong("ms",300),VibrationEffect.DEFAULT_AMPLITUDE)) }
        "clipboard.write"->{ (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("JARVIS",a.optString("text"))) }
        "open_url"->{ startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(a.getString("url"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        "app.launch"->{ val query=a.optString("package").ifBlank { a.optString("app") }.ifBlank { a.optString("name") }; val pkg=resolveAppPackage(query)?:error("App not found: $query"); val i=packageManager.getLaunchIntentForPackage(pkg)?:error("App has no launch activity: $pkg"); startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); result="opened $pkg" }
        "app.close"->{ val svc=JarvisAccessibilityService.instance?:error("Accessibility control is disabled on the phone"); result=svc.global("home") }
        "android.settings.open"->{ val page=a.optString("page").ifBlank { a.optString("section") }; startActivity(settingsIntent(page)); result=if(page.isBlank()) "opened Android Settings" else "opened Android Settings: $page" }
        "android.ui.inspect"->{ val svc=JarvisAccessibilityService.instance?:error("Accessibility control is disabled on the phone"); result=svc.inspect(a.optInt("max_nodes",120)).toString() }
        "android.ui.click"->{ val svc=JarvisAccessibilityService.instance?:error("Accessibility control is disabled on the phone"); result=svc.click(a.optString("text"),a.optString("view_id")) }
        "android.ui.text"->{ val svc=JarvisAccessibilityService.instance?:error("Accessibility control is disabled on the phone"); result=svc.setText(a.getString("text"),a.optString("target_text"),a.optString("view_id")) }
        "android.ui.scroll"->{ val svc=JarvisAccessibilityService.instance?:error("Accessibility control is disabled on the phone"); result=svc.scroll(a.optString("direction","down")) }
        "android.ui.global"->{ val svc=JarvisAccessibilityService.instance?:error("Accessibility control is disabled on the phone"); result=svc.global(a.getString("action")) }
        "android.screen.lock"->{ val svc=JarvisAccessibilityService.instance?:error("Accessibility control is disabled on the phone"); result=svc.global("lock") }
        "android.screen.wake"->{ val pm=getSystemService(POWER_SERVICE) as PowerManager; if(!pm.isInteractive){ @Suppress("DEPRECATION") val wl=pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,"jarvis:wake"); wl.acquire(3000) }; result="screen awake; device authentication is still required" }
        else->{ok=false;result="Unsupported capability: $cap"}
    }}catch(e:Exception){ok=false;result=e.message?:e.toString()}; w.send(JSONObject().put("type","capability.result").put("call_id",m.optString("call_id")).put("ok",ok).put("result",result).toString()) }

    private fun normalizeName(s:String)=s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    private fun resolveAppPackage(query:String):String? {
        if(query.isBlank()) return null
        packageManager.getLaunchIntentForPackage(query)?.let { return query }
        val q=normalizeName(query)
        val aliases=mapOf("whatsapp" to "com.whatsapp", "wa" to "com.whatsapp", "youtube" to "com.google.android.youtube", "chrome" to "com.android.chrome", "gmail" to "com.google.android.gm", "maps" to "com.google.android.apps.maps", "googlemaps" to "com.google.android.apps.maps")
        aliases[q]?.let { if(packageManager.getLaunchIntentForPackage(it)!=null) return it }
        val launcher=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val acts=if(Build.VERSION.SDK_INT>=33) packageManager.queryIntentActivities(launcher,PackageManager.ResolveInfoFlags.of(0)) else @Suppress("DEPRECATION") packageManager.queryIntentActivities(launcher,0)
        return acts.asSequence().map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }.sortedByDescending { val n=normalizeName(it.second); when { n==q -> 3; n.contains(q)||q.contains(n) -> 2; normalizeName(it.first).contains(q) -> 1; else -> 0 } }.firstOrNull { val n=normalizeName(it.second); n==q || n.contains(q) || q.contains(n) || normalizeName(it.first).contains(q) }?.first
    }

    private fun settingsIntent(raw:String):Intent {
        val p=normalizeName(raw)
        val action=when {
            p.contains("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS
            p.contains("wifi") || p.contains("wireless") -> Settings.ACTION_WIFI_SETTINGS
            p.contains("accessibility") -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            p.contains("notification") -> "android.settings.NOTIFICATION_SETTINGS"
            p.contains("display") || p.contains("screen") -> Settings.ACTION_DISPLAY_SETTINGS
            p.contains("sound") || p.contains("audio") -> Settings.ACTION_SOUND_SETTINGS
            p.contains("location") -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            p.contains("security") -> Settings.ACTION_SECURITY_SETTINGS
            p.contains("application") || p=="apps" || p=="app" -> Settings.ACTION_APPLICATION_SETTINGS
            p.contains("battery") -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            p.contains("date") || p.contains("time") -> Settings.ACTION_DATE_SETTINGS
            p.contains("language") || p.contains("keyboard") -> Settings.ACTION_INPUT_METHOD_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun onDestroy(){ stopMic(); try{player?.stop()}catch(_:Exception){}; player?.release(); player=null; ws?.close(1000,"activity closed"); super.onDestroy() }
    private fun ui(s:String)=runOnUiThread{status.text=s}
    private fun pairUi(s:String)=runOnUiThread{pairStatus.text=s}
    private fun b64(b:ByteArray)=Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private fun unb64(s:String)=Base64.getUrlDecoder().decode(s)
    private fun lanClient():OkHttpClient { val tm=object:X509TrustManager{override fun getAcceptedIssuers()=arrayOf<X509Certificate>();override fun checkClientTrusted(c:Array<X509Certificate>,a:String){};override fun checkServerTrusted(c:Array<X509Certificate>,a:String){}}; val sc=SSLContext.getInstance("TLS");sc.init(null,arrayOf<TrustManager>(tm),SecureRandom());return OkHttpClient.Builder().sslSocketFactory(sc.socketFactory,tm).hostnameVerifier{_,_->true}.pingInterval(20,TimeUnit.SECONDS).build() }
}
