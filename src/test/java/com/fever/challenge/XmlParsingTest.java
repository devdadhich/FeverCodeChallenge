package com.fever.challenge;

import com.fever.challenge.client.ProviderXmlModels.PlanList;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class XmlParsingTest {

    private static final String SAMPLE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <planList xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="1.0">
               <output>
                  <base_plan base_plan_id="291" sell_mode="online" title="Camela en concierto">
                     <plan plan_start_date="2021-06-30T21:00:00" plan_end_date="2021-06-30T22:00:00"
                           plan_id="291" sell_from="2020-07-01T00:00:00" sell_to="2021-06-30T20:00:00" sold_out="false">
                        <zone zone_id="40" capacity="243" price="20.00" name="Platea" numbered="true" />
                        <zone zone_id="38" capacity="100" price="15.00" name="Grada 2" numbered="false" />
                        <zone zone_id="30" capacity="90" price="30.00" name="A28" numbered="true" />
                     </plan>
                  </base_plan>
                  <base_plan base_plan_id="444" sell_mode="offline" title="Offline Event">
                     <plan plan_start_date="2021-09-31T20:00:00" plan_end_date="2021-09-31T21:00:00"
                           plan_id="1642" sell_from="2021-02-10T00:00:00" sell_to="2021-09-31T19:50:00" sold_out="false">
                        <zone zone_id="7" capacity="22" price="65.00" name="Amfiteatre" numbered="false" />
                     </plan>
                  </base_plan>
               </output>
            </planList>
            """;

    @Test
    void shouldParseProviderXml() throws Exception {
        JAXBContext context = JAXBContext.newInstance(PlanList.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        PlanList planList = (PlanList) unmarshaller.unmarshal(new StringReader(SAMPLE_XML));

        assertNotNull(planList);
        assertNotNull(planList.getOutput());
        assertEquals(2, planList.getOutput().getBasePlans().size());

        var onlinePlan = planList.getOutput().getBasePlans().get(0);
        assertEquals("291", onlinePlan.getBasePlanId());
        assertEquals("online", onlinePlan.getSellMode());
        assertEquals("Camela en concierto", onlinePlan.getTitle());
        assertEquals(1, onlinePlan.getPlans().size());
        assertEquals(3, onlinePlan.getPlans().get(0).getZones().size());

        var offlinePlan = planList.getOutput().getBasePlans().get(1);
        assertEquals("offline", offlinePlan.getSellMode());
    }

    @Test
    void shouldExtractPricesFromZones() throws Exception {
        JAXBContext context = JAXBContext.newInstance(PlanList.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        PlanList planList = (PlanList) unmarshaller.unmarshal(new StringReader(SAMPLE_XML));

        var zones = planList.getOutput().getBasePlans().get(0).getPlans().get(0).getZones();
        assertEquals("20.00", zones.get(0).getPrice());
        assertEquals("15.00", zones.get(1).getPrice());
        assertEquals("30.00", zones.get(2).getPrice());
    }
}
