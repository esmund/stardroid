// Copyright 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
public class ModStarsLayer extends AbstractFileBasedLayer {
    public ModStarsLayer(AssetManager assetManager, Resources resources) {
        super(assetManager, resources, "stars-mod.binary");
    }

    @Override
    public int getLayerDepthOrder() {
        return 30;
    }

    @Override
    protected int getLayerNameId() {
        return R.string.show_mod_stars_pref;
    }

    // TODO(brent): Remove this.
    @Override
    public String getPreferenceId() {
        return "source_provider.7";
    }
}
