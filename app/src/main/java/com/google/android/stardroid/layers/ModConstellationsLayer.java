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
public class ModConstellationsLayer extends AbstractFileBasedLayer {
    public ModConstellationsLayer(AssetManager assetManager, Resources resources) {
        super(assetManager, resources, "constellations-mod.binary");
    }

    @Override
    public int getLayerDepthOrder() {
        return 10;
    }

    @Override
    protected int getLayerNameId() {
        return R.string.show_mod_constellations_pref;
    }

    // TODO(brent): Remove this.
    @Override
    public String getPreferenceId() {
        return "source_provider.9";
    }
}