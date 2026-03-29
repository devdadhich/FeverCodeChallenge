package com.fever.challenge.client;

import jakarta.xml.bind.annotation.*;
import java.util.List;

/**
 * JAXB models for parsing the external provider's XML response.
 * These are kept together in one file for simplicity since they are
 * only used for deserialization.
 */
public class ProviderXmlModels {

    @XmlRootElement(name = "planList")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PlanList {
        @XmlElement(name = "output")
        private Output output;

        public Output getOutput() { return output; }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Output {
        @XmlElement(name = "base_plan")
        private List<BasePlan> basePlans;

        public List<BasePlan> getBasePlans() { return basePlans; }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class BasePlan {
        @XmlAttribute(name = "base_plan_id")
        private String basePlanId;

        @XmlAttribute(name = "sell_mode")
        private String sellMode;

        @XmlAttribute(name = "title")
        private String title;

        @XmlAttribute(name = "organizer_company_id")
        private String organizerCompanyId;

        @XmlElement(name = "plan")
        private List<Plan> plans;

        public String getBasePlanId() { return basePlanId; }
        public String getSellMode() { return sellMode; }
        public String getTitle() { return title; }
        public List<Plan> getPlans() { return plans; }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Plan {
        @XmlAttribute(name = "plan_id")
        private String planId;

        @XmlAttribute(name = "plan_start_date")
        private String planStartDate;

        @XmlAttribute(name = "plan_end_date")
        private String planEndDate;

        @XmlAttribute(name = "sell_from")
        private String sellFrom;

        @XmlAttribute(name = "sell_to")
        private String sellTo;

        @XmlAttribute(name = "sold_out")
        private String soldOut;

        @XmlElement(name = "zone")
        private List<Zone> zones;

        public String getPlanId() { return planId; }
        public String getPlanStartDate() { return planStartDate; }
        public String getPlanEndDate() { return planEndDate; }
        public String getSellFrom() { return sellFrom; }
        public String getSellTo() { return sellTo; }
        public List<Zone> getZones() { return zones; }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Zone {
        @XmlAttribute(name = "zone_id")
        private String zoneId;

        @XmlAttribute(name = "capacity")
        private int capacity;

        @XmlAttribute(name = "price")
        private String price;

        @XmlAttribute(name = "name")
        private String name;

        @XmlAttribute(name = "numbered")
        private boolean numbered;

        public String getPrice() { return price; }
        public int getCapacity() { return capacity; }
    }
}
