DATA_DIR=data
TOOL=build/install/datagen/bin/datagen

# Note, constellation data is already in proto form.
$TOOL GenMessier $DATA_DIR/modified/messier-mod.csv $DATA_DIR/modified/messier
$TOOL GenStars $DATA_DIR/modified/stardata_names-mod.txt $DATA_DIR/modified/stars


