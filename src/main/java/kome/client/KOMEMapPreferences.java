package kome.client;

/** Immutable installation-local appearance snapshot. No world, player ID or network data. */
final class KOMEMapPreferences {
    final KOMETerritoryStyle.Palette palette;
    final KOMETerritoryStyle.DisplayMode mode;
    final KOMETerritoryStyle.Opacity opacity;
    final boolean tileBorders, conquest, rivers, bridges, troops, builds;
    KOMEMapPreferences(){this(KOMETerritoryStyle.Palette.CLASSIC_LOTR,KOMETerritoryStyle.DisplayMode.FULL_COLOR,
        KOMETerritoryStyle.Opacity.HALF,false,true,true,true,true);}
    KOMEMapPreferences(KOMETerritoryStyle.Palette palette,KOMETerritoryStyle.DisplayMode mode,KOMETerritoryStyle.Opacity opacity,
            boolean tileBorders,boolean conquest,boolean rivers,boolean bridges,boolean troops) {
        this(palette,mode,opacity,tileBorders,conquest,rivers,bridges,troops,true);
    }
    KOMEMapPreferences(KOMETerritoryStyle.Palette palette,KOMETerritoryStyle.DisplayMode mode,KOMETerritoryStyle.Opacity opacity,
            boolean tileBorders,boolean conquest,boolean rivers,boolean bridges,boolean troops,boolean builds) {
        this.palette=palette;this.mode=mode;this.opacity=opacity;this.tileBorders=tileBorders;
        this.conquest=conquest;this.rivers=rivers;this.bridges=bridges;this.troops=troops;this.builds=builds;
    }
    KOMEMapPreferences withConquest(boolean visible){return new KOMEMapPreferences(palette,mode,opacity,tileBorders,visible,rivers,bridges,troops,builds);}
    @Override public boolean equals(Object other) {
        if(!(other instanceof KOMEMapPreferences))return false;KOMEMapPreferences p=(KOMEMapPreferences)other;
        return palette==p.palette&&mode==p.mode&&opacity==p.opacity&&tileBorders==p.tileBorders
            &&conquest==p.conquest&&rivers==p.rivers&&bridges==p.bridges&&troops==p.troops&&builds==p.builds;
    }
    @Override public int hashCode(){return java.util.Objects.hash(palette,mode,opacity,tileBorders,conquest,rivers,bridges,troops,builds);}
}
