package com.miniledger.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * 玻璃皮肤的底：一层浅灰加几个柔和的色团，
 * 上面压半透明白卡片，读起来就是磨砂玻璃。
 * 不用 RenderEffect 模糊，26 上也一样跑。
 */
class Aurora extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RadialGradient g1, g2, g3, g4, g5;
    private float w, h;

    Aurora(Context c) {
        super(c);
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        super.onSizeChanged(nw, nh, ow, oh);
        w = nw;
        h = nh;
        g1 = blob(w * 0.10f, h * 0.04f, w * 0.95f, 0xBD9FE3D8);
        g2 = blob(w * 0.98f, h * 0.22f, w * 0.85f, 0xA3C7DDF8);
        g3 = blob(w * 0.02f, h * 0.84f, w * 0.90f, 0x99FBDCC4);
        g4 = blob(w * 0.94f, h * 0.98f, w * 0.78f, 0x8A70D6C2);
        g5 = blob(w * 0.56f, h * 0.52f, w * 0.86f, 0x80A7DCEC);
    }

    private RadialGradient blob(float cx, float cy, float r, int color) {
        return new RadialGradient(cx, cy, r,
                new int[]{color, color & 0x00FFFFFF},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Ui.BG);
        draw(canvas, g1, w * 0.10f, h * 0.04f, w * 0.95f);
        draw(canvas, g2, w * 0.98f, h * 0.22f, w * 0.85f);
        draw(canvas, g3, w * 0.02f, h * 0.84f, w * 0.90f);
        draw(canvas, g4, w * 0.94f, h * 0.98f, w * 0.78f);
        draw(canvas, g5, w * 0.56f, h * 0.52f, w * 0.86f);
    }

    private void draw(Canvas canvas, RadialGradient g, float cx, float cy, float r) {
        if (g == null) return;
        paint.setShader(g);
        canvas.drawCircle(cx, cy, r, paint);
        paint.setShader(null);
    }

    static int base() {
        return Color.rgb(0xF2, 0xF5, 0xF8);
    }
}
