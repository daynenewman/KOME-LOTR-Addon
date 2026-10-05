package kome.common.data;

import java.util.ArrayList;
import java.util.List;

/** Saved private correspondence. No objective text or technical identifiers belong here. */
public final class KOMECourierCorrespondence {
    public interface Width { int pixels(String text); }
    private KOMECourierCorrespondence() { }
    public static String compose(KOMESerfCourierAssignment a,KOMEProgressionNpcRef master) {
        String to=a.recipientName.length()>0?a.recipientName:a.recipient.isSet()?a.recipient.displayName:"My correspondent";
        String culture=KOMEAlliance.normalizeFactionKey(master.factionKey);
        boolean dark=culture.equals("mordor")||culture.equals("angmar")||culture.equals("gundabad")||culture.equals("dolguldur")||culture.equals("isengard")||culture.equals("halftroll");
        boolean dwarf=culture.equals("durinsfolk")||culture.equals("bluemountains");
        boolean elf=culture.equals("highelves")||culture.equals("woodelf")||culture.equals("lothlorien");
        String concern;
        if("captain".equals(a.recipientRole)) concern="Tell me what your patrols require. I would rather hear of a worn strap or a damaged bowstring now than learn that a watch went short of them.";
        else if("smith".equals(a.recipientRole)||"smith".equals(a.masterRole)) concern="When you next inspect the tools, set aside those that can still be repaired. Let me know what metal and fuel the work would take; there is no sense wasting sound iron.";
        else if("vintner".equals(a.recipientRole)||"vintner".equals(a.masterRole)) concern="Let me know how the vines are faring and whether the casks need attention. I would welcome a careful account before making any promises about the next pressing.";
        else if("merchant".equals(a.recipientRole)) concern="Please send a fair account of the goods you can spare and those you need. Mark any damaged packing plainly, so that we may agree on its repair before another load travels.";
        else switch(a.storyVariant) {
            case 0: concern="If any stores are running short, send me a careful tally. Count what is sound separately from what is spoiled; a fair reckoning will help us decide what can be spared.";break;
            case 1: concern="I would value your judgement on the repairs that can wait and those that cannot. Tell me what hands and materials each would need, rather than making a hasty promise.";break;
            case 2: concern="When there is a quiet hour, look over the tools and their handles. Small faults are easier to mend before they become costly ones. Send me your thoughts on the work.";break;
            case 3: concern="If the season has been kind to your stores, let me know what surplus you would exchange. If it has not, write honestly of what is lacking, and we can consider what is possible.";break;
            default: concern="I hope to settle our next exchange without needless waste. Tell me what can be packed safely and what ought to wait, and I will give your account due attention.";
        }
        String opening=dark?"I expect a straight account, not excuses.":dwarf?"May your hearth hold its warmth and your work its worth.":elf?"May this letter find you beneath a peaceful sky.":culture.equals("rohan")?"May your household fare well, and your road be an easy one.":"I hope this finds you and your household well.";
        String closing=dark?"See that the account is accurate.":dwarf?"With a fair word and a firm hand,":elf?"In friendship and good remembrance,":"With my good regards,";
        String[] postscripts={"There is no need for haste at the expense of a true account.","A small difficulty named plainly is easier to put right.","I would welcome your own judgement as well as the tally.","Keep a copy of your reckoning, so that neither of us need trust to memory.","We will settle the details when your answer reaches me."};
        return to+",\n\n"+opening+"\n\n"+concern+"\n\n"+postscripts[a.storyVariant]+"\n\n"+closing+"\n"+master.displayName;
    }
    /** Vanilla/LOTR lore books use a 116-pixel text column and 128-pixel height.
     * 13 lines and 256 characters also satisfy native written-book validation.
     * The client supplies its actual font widths; server pages use safe upper bounds. */
    public static List<String> pages(String text,Width width) {
        List<String> result=new ArrayList<String>();
        StringBuilder page=new StringBuilder();int lines=0;
        for(String paragraph:text.replace("\r","").split("\n",-1)) {
            List<String> wrapped=new ArrayList<String>();String line="";
            for(String word:paragraph.split(" +")) {
                if(word.length()==0)continue;
                while(width.pixels(word)>116) {
                    int cut=1;while(cut<word.length()&&width.pixels(word.substring(0,cut+1))<=116)cut++;
                    if(line.length()>0){wrapped.add(line);line="";}
                    wrapped.add(word.substring(0,cut));word=word.substring(cut);
                }
                String candidate=line.length()==0?word:line+" "+word;
                if(width.pixels(candidate)>116){wrapped.add(line);line=word;}else line=candidate;
            }
            if(line.length()>0)wrapped.add(line);
            if(wrapped.isEmpty())wrapped.add("");
            // Keep a paragraph together when it fits a page.
            int chars=0;for(String value:wrapped)chars+=value.length()+1;
            if(page.length()>0&&wrapped.size()<=13&&chars<=256&&(lines+wrapped.size()>13||page.length()+chars>256)) {
                result.add(page.toString());page.setLength(0);lines=0;
            }
            for(String value:wrapped) {
                if(lines>=13||page.length()+value.length()+(lines>0?1:0)>256){result.add(page.toString());page.setLength(0);lines=0;}
                if(lines>0)page.append('\n');page.append(value);lines++;
            }
        }
        if(page.length()>0)result.add(page.toString());
        if(result.isEmpty())result.add("");return result;
    }
    public static List<String> pages(String text) {
        return pages(text,new Width(){public int pixels(String s){int total=0;for(char c:s.toCharArray())total+=c==' '?4:8;return total;}});
    }
}
