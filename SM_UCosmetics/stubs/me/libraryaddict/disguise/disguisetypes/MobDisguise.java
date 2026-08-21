package me.libraryaddict.disguise.disguisetypes;

public class MobDisguise {
    public MobDisguise(DisguiseType type) {}
    public MobDisguise(DisguiseType type, boolean adult) {}
    public FlagWatcher getWatcher() { return new FlagWatcher(); }
    public void setReplaceSounds(boolean replace) {}
    public void setShowName(boolean show) {}
    public void setViewSelfDisguise(boolean viewSelf) {}
}

