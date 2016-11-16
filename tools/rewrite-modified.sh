#!/bin/bash
TOOL=build/install/datagen/bin/datagen
DATA_DIR=data

$TOOL Rewrite $DATA_DIR/modified/stars_R.ascii
$TOOL Rewrite $DATA_DIR/modified/messier_R.ascii
$TOOL Rewrite $DATA_DIR/modified/constellations_R.ascii
