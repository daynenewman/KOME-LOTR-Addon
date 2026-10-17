package kome.client.gui;

import java.util.List;
import kome.common.data.KOMEProgressionRankSummary;

/** Pure layout measurements shared by rank rendering and its focused tests. */
final class KOMEProgressionRankLayout {
    static final int REQUIREMENT_HEIGHT = 26;
    static final int CHILD_HEIGHT = 20;

    private KOMEProgressionRankLayout() {}
    static int standingHeight(String current,String faction) {
        for(String title:kome.common.data.KOMEFactionProgressionTitles.ladder(faction))if(title.equals(current))return 0;
        return 14;
    }
    static int headingHeight(int lines) { return 4+10*Math.max(0,lines); }
    static int activityHeight(int titleLines,int objectiveLines) { return 27+10*(titleLines+objectiveLines); }
    static int abilityHeight(List<kome.common.data.KOMEProgressionAbilityPolicy.Outcome> rows) {
        boolean current=false,next=false;
        for(kome.common.data.KOMEProgressionAbilityPolicy.Outcome row:rows){if(row.rankUnlocked)current=true;else next=true;}
        return rows.size()*20+(current?20:0)+(next?20:0);
    }

    static int requirementHeight(KOMEProgressionRankSummary.Requirement requirement,
            boolean childrenExpanded) {
        return REQUIREMENT_HEIGHT + (childrenExpanded && requirement.hasChildren()
            ? requirement.children.size() * CHILD_HEIGHT : 0);
    }

    static int requirementsHeight(List<KOMEProgressionRankSummary.Requirement> requirements,
            boolean childrenExpanded) {
        int height = 0;
        for (KOMEProgressionRankSummary.Requirement requirement : requirements) {
            height += requirementHeight(requirement, childrenExpanded);
        }
        return height;
    }
}
