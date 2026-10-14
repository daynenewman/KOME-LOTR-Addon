package kome.client;

import java.util.List;
import kome.common.data.KOMECompanyDestinationOptions;
import kome.common.data.KOMECompanyDestinationOptions.Row;

/** Movement adapter: 23 window rows + 8 accepted query rows + one selected row = 32 projections. */
final class KOMECompanyBrowseWindow extends KOMECompanyListWindow<Row> {
    KOMECompanyBrowseWindow(){super(row->row.companyId);}
    void reset(int index,int start,KOMECompanyDestinationOptions page,List<Row> matching){super.reset(index,start,page.rows,page.nextCompanyId,page.catalogueToken,page.hasMore,matching);}
    void update(int index,int start,KOMECompanyDestinationOptions page,List<Row> matching){
        super.update(index,start,page.rows,page.nextCompanyId,page.catalogueToken,page.hasMore,matching);
    }
    void refilter(KOMECompanyDestinationSelector.Filter next){super.refilter(next::matches);}
}
