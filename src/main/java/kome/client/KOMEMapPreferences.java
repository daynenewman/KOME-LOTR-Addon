package kome.client;

/** Immutable installation-local appearance snapshot. No world, player ID or network data. */
final class KOMEMapPreferences {
    enum CompanyDisplay {
        HIDE_COMPANIES, SHIELDS_ONLY, SHIELDS_AND_DESCRIPTORS;
        CompanyDisplay next(){return values()[(ordinal()+1)%values().length];}
        boolean visible(){return this!=HIDE_COMPANIES;}
        String label(){switch(this){
            case HIDE_COMPANIES:return "Hide Companies";
            case SHIELDS_AND_DESCRIPTORS:return "Shields + Descriptors";
            default:return "Shields Only";
        }}
    }
    final KOMETerritoryStyle.Palette palette;
    final KOMETerritoryStyle.DisplayMode mode;
    final KOMETerritoryStyle.Opacity opacity;
    final boolean tileBorders, conquest, rivers, bridges, troops, builds;
    final CompanyDisplay companyDisplay;
    KOMEMapPreferences(){this(KOMETerritoryStyle.Palette.CLASSIC_LOTR,KOMETerritoryStyle.DisplayMode.FULL_COLOR,
        KOMETerritoryStyle.Opacity.HALF,false,true,true,true,true);}
    KOMEMapPreferences(KOMETerritoryStyle.Palette palette,KOMETerritoryStyle.DisplayMode mode,KOMETerritoryStyle.Opacity opacity,
            boolean tileBorders,boolean conquest,boolean rivers,boolean bridges,boolean troops) {
        this(palette,mode,opacity,tileBorders,conquest,rivers,bridges,troops,true);
    }
    KOMEMapPreferences(KOMETerritoryStyle.Palette palette,KOMETerritoryStyle.DisplayMode mode,KOMETerritoryStyle.Opacity opacity,
            boolean tileBorders,boolean conquest,boolean rivers,boolean bridges,boolean troops,boolean builds) {
        this(palette,mode,opacity,tileBorders,conquest,rivers,bridges,
            troops?CompanyDisplay.SHIELDS_ONLY:CompanyDisplay.HIDE_COMPANIES,builds);
    }
    KOMEMapPreferences(KOMETerritoryStyle.Palette palette,KOMETerritoryStyle.DisplayMode mode,KOMETerritoryStyle.Opacity opacity,
            boolean tileBorders,boolean conquest,boolean rivers,boolean bridges,CompanyDisplay companyDisplay,boolean builds) {
        this.palette=palette;this.mode=mode;this.opacity=opacity;this.tileBorders=tileBorders;
        this.conquest=conquest;this.rivers=rivers;this.bridges=bridges;
        this.companyDisplay=companyDisplay;this.troops=companyDisplay.visible();this.builds=builds;
    }
    KOMEMapPreferences withConquest(boolean visible){return new KOMEMapPreferences(palette,mode,opacity,tileBorders,visible,rivers,bridges,companyDisplay,builds);}
    KOMEMapPreferences withCompanyDisplay(CompanyDisplay selected){return new KOMEMapPreferences(palette,mode,opacity,tileBorders,conquest,rivers,bridges,selected,builds);}
    @Override public boolean equals(Object other) {
        if(!(other instanceof KOMEMapPreferences))return false;KOMEMapPreferences p=(KOMEMapPreferences)other;
        return palette==p.palette&&mode==p.mode&&opacity==p.opacity&&tileBorders==p.tileBorders
            &&conquest==p.conquest&&rivers==p.rivers&&bridges==p.bridges&&companyDisplay==p.companyDisplay&&builds==p.builds;
    }
    @Override public int hashCode(){return java.util.Objects.hash(palette,mode,opacity,tileBorders,conquest,rivers,bridges,companyDisplay,builds);}
}
