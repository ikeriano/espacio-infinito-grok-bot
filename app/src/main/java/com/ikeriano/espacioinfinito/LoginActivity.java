package com.ikeriano.espacioinfinito;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

/** Inicio de sesión con tu propia cuenta de Telegram: número + código (+ contraseña si tienes verificación en dos pasos). */
public class LoginActivity extends Activity {
    private TextView err;
    private final Tg.Listener l = new Tg.Listener() { public void onAuth(TdApi.AuthorizationState s) { runOnUiThread(new Runnable() { public void run() { build(); } }); } };

    @Override protected void onResume() { super.onResume(); Tg.get().listeners.add(l); Tg.get().start(this); build(); }
    @Override protected void onPause() { super.onPause(); Tg.get().listeners.remove(l); }

    void build() {
        LinearLayout p = Ui.page(this);
        p.addView(Ui.title(this, "Conectar con Telegram"));
        err = Ui.text(this, ""); err.setTextColor(Ui.RED);
        TdApi.AuthorizationState s = Tg.get().auth();
        if (!Tg.hasKeys(this)) { keys(p); }
        else if (!Tg.libAvailable()) { p.addView(Ui.text(this, "Este móvil no puede cargar el motor de Telegram (TDLib).")); }
        else if (s == null || s instanceof TdApi.AuthorizationStateWaitTdlibParameters) {
            p.addView(Ui.text(this, "Conectando con Telegram…"));
            p.addView(Ui.big(this, "Cambiar api_id / api_hash", Ui.GREY, new View.OnClickListener() { public void onClick(View v) {
                Tg.prefs(LoginActivity.this).edit().remove("api_id").remove("api_hash").apply(); build(); } }));
        }
        else if (s instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            p.addView(Ui.text(this, "Escribe tu número de teléfono con el prefijo del país, por ejemplo +34 600 123 456."));
            final EditText ph = Ui.input(this, "+34 …", Ui.PHONE); p.addView(ph);
            p.addView(Ui.big(this, "Enviarme el código", Ui.BLUE, new View.OnClickListener() { public void onClick(View v) {
                send(new TdApi.SetAuthenticationPhoneNumber(ph.getText().toString().replace(" ", ""), null)); } }));
        } else if (s instanceof TdApi.AuthorizationStateWaitCode) {
            p.addView(Ui.text(this, "Te hemos enviado un código. Mira en la app de Telegram (chat «Telegram») o en tus SMS y escríbelo aquí."));
            final EditText c = Ui.input(this, "Código", Ui.NUMBER); p.addView(c);
            p.addView(Ui.big(this, "Entrar", Ui.GREEN, new View.OnClickListener() { public void onClick(View v) {
                send(new TdApi.CheckAuthenticationCode(c.getText().toString().trim())); } }));
        } else if (s instanceof TdApi.AuthorizationStateWaitPassword) {
            String hint = ((TdApi.AuthorizationStateWaitPassword) s).passwordHint;
            p.addView(Ui.text(this, "Tu cuenta tiene verificación en dos pasos. Escribe tu contraseña de Telegram." + (hint != null && !hint.isEmpty() ? " Pista: " + hint : "")));
            final EditText c = Ui.input(this, "Contraseña", Ui.PASSWORD); p.addView(c);
            p.addView(Ui.big(this, "Entrar", Ui.GREEN, new View.OnClickListener() { public void onClick(View v) {
                send(new TdApi.CheckAuthenticationPassword(c.getText().toString())); } }));
        } else if (s instanceof TdApi.AuthorizationStateReady) {
            p.addView(Ui.text(this, "¡Conectado! Ya tienes espacio sin límite."));
            p.addView(Ui.big(this, "Empezar", Ui.GREEN, new View.OnClickListener() { public void onClick(View v) { finish(); } }));
        } else {
            p.addView(Ui.text(this, "Telegram pide un paso que esta app no gestiona (" + s.getClass().getSimpleName().replace("AuthorizationState", "") +
                    "). Abre la app oficial de Telegram, termina allí el registro y vuelve a intentarlo."));
        }
        p.addView(err);
        p.addView(Ui.small(this, "Tus archivos se guardan en tus «Mensajes guardados» de Telegram, solo tú puedes verlos."));
    }

    private void keys(LinearLayout p) {
        p.addView(Ui.text(this, "Solo una vez: Telegram pide unas claves gratuitas para las apps. Se consiguen en 2 minutos:"));
        p.addView(Ui.text(this, "1. Abre my.telegram.org y entra con tu número (el código llega a tu Telegram).\n" +
                "2. Pulsa «API development tools».\n3. Rellena: App title «Espacio Infinito», Short name «espacioinf», Platform «Android». Pulsa «Create application».\n" +
                "4. Copia aquí el «App api_id» (números) y el «App api_hash» (letras y números)."));
        p.addView(Ui.big(this, "Abrir my.telegram.org", Ui.BLUE, new View.OnClickListener() { public void onClick(View v) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://my.telegram.org/apps"))); } }));
        final EditText id = Ui.input(this, "api_id (ej. 1234567)", Ui.NUMBER); p.addView(id);
        final EditText hash = Ui.input(this, "api_hash", Ui.TEXT); p.addView(hash);
        p.addView(Ui.big(this, "Guardar y continuar", Ui.GREEN, new View.OnClickListener() { public void onClick(View v) {
            String i = id.getText().toString().trim(), hh = hash.getText().toString().trim();
            if (!i.matches("\\d{3,12}") || !hh.matches("[0-9a-fA-F]{32}")) { err.setText("Revisa los datos: el api_id son solo números y el api_hash tiene 32 letras/números."); return; }
            Tg.prefs(LoginActivity.this).edit().putInt("api_id", Integer.parseInt(i)).putString("api_hash", hh.toLowerCase()).apply();
            Tg.get().start(LoginActivity.this); build();
        } }));
    }

    private void send(TdApi.Function f) {
        err.setText("Un momento…");
        Tg.get().sendAsync(f, new Client.ResultHandler() { public void onResult(final TdApi.Object o) { runOnUiThread(new Runnable() { public void run() {
            if (o instanceof TdApi.Error) err.setText(friendly(((TdApi.Error) o).message)); else err.setText("");
        } }); } });
    }
    static String friendly(String m) {
        if (m == null) return "Algo ha fallado. Inténtalo de nuevo.";
        if (m.contains("PHONE_NUMBER_INVALID")) return "Ese número no parece correcto. Pon el prefijo, por ejemplo +34.";
        if (m.contains("PHONE_CODE_INVALID")) return "El código no es correcto. Revísalo.";
        if (m.contains("PHONE_CODE_EXPIRED")) return "El código ha caducado. Vuelve atrás y pide otro.";
        if (m.contains("PASSWORD_HASH_INVALID")) return "La contraseña no es correcta.";
        if (m.contains("FLOOD")) return "Demasiados intentos. Espera un rato y vuelve a probar.";
        if (m.contains("API_ID")) return "El api_id o api_hash no son válidos. Revísalos en Ajustes.";
        return "Telegram dice: " + m;
    }
}
