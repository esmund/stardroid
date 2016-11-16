package com.google.android.stardroid.layers;

import android.content.res.AssetManager;
import android.content.res.Resources;

import com.google.android.stardroid.R;

/**
 * An implementation of the {@link AbstractFileBasedLayer} for displaying stars
 * in the Renderer.
 *
 * @author John Taylor
 * @author Brent Bryan
 */
public class ModMessierLayer extends AbstractFileBasedLayer {
    public ModMessierLayer(AssetManager assetManager, Resources resources) {
        super(assetManager, resources, "messier-mod.binary");
    }

    @Override
    public int getLayerDepthOrder() {
        return 20;
    }

    @Override
    protected int getLayerNameId() {
        return R.string.show_mod_messier_objects_pref;
    }

    // TODO(brent): Remove this.
    @Override
    public String getPreferenceId() {
        return "source_provider.8";
    }
}
