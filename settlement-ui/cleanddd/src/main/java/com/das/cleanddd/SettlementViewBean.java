package com.das.cleanddd;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonProperty;

@Controller
public class SettlementViewBean {

    private final RestTemplate restTemplate;

    @Value("${settlement.api.base-url:http://localhost:8080/api/v1/settlement}")
    private String apiBaseUrl;

    @Autowired
    public SettlementViewBean(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/")
    public String home(Model model) {
        List<SettlementSummary> settlements = loadSettlements();
        model.addAttribute("settlements", settlements);
        model.addAttribute("form", new SettlementForm());
        return "index";
    }

    @PostMapping("/settlements")
    public String createSettlement(@ModelAttribute("form") SettlementForm form, Model model) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            CreateRequest request = new CreateRequest(form.description, form.settlementDate, List.of(), form.medicalSalesRepId);
            HttpEntity<CreateRequest> entity = new HttpEntity<>(request, headers);
            restTemplate.postForEntity(apiBaseUrl + "/create", entity, Object.class);
            model.addAttribute("successMessage", "Settlement created successfully.");
        } catch (Exception ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }

        List<SettlementSummary> settlements = loadSettlements();
        model.addAttribute("settlements", settlements);
        model.addAttribute("form", form);
        return "index";
    }

    private List<SettlementSummary> loadSettlements() {
        try {
            String url = apiBaseUrl + "/list?page=1&pageSize=10";
            SettlementSummary[] payload = restTemplate.postForObject(url, null, SettlementSummary[].class);
            return payload == null ? new ArrayList<>() : List.of(payload);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    public static class SettlementForm {
        private String description;
        private LocalDate settlementDate;
        private String medicalSalesRepId;

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public LocalDate getSettlementDate() { return settlementDate; }
        public void setSettlementDate(LocalDate settlementDate) { this.settlementDate = settlementDate; }
        public String getMedicalSalesRepId() { return medicalSalesRepId; }
        public void setMedicalSalesRepId(String medicalSalesRepId) { this.medicalSalesRepId = medicalSalesRepId; }
    }

    public static class SettlementSummary {
        private String id;
        private String description;
        private LocalDate settlementDate;
        private String status;
        private BigDecimal totalAmount;
        private List<Object> invoices;
        private String medicalSalesRepId;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public LocalDate getSettlementDate() { return settlementDate; }
        public void setSettlementDate(LocalDate settlementDate) { this.settlementDate = settlementDate; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public List<Object> getInvoices() { return invoices; }
        public void setInvoices(List<Object> invoices) { this.invoices = invoices; }
        public String getMedicalSalesRepId() { return medicalSalesRepId; }
        public void setMedicalSalesRepId(String medicalSalesRepId) { this.medicalSalesRepId = medicalSalesRepId; }
    }

    public static class CreateRequest {
        @JsonProperty("description")
        private String description;

        @JsonProperty("settlementDate")
        private LocalDate settlementDate;

        @JsonProperty("invoices")
        private List<Object> invoices;

        @JsonProperty("medicalSalesRepId")
        private String medicalSalesRepId;

        public CreateRequest() {
        }

        public CreateRequest(String description, LocalDate settlementDate, List<Object> invoices, String medicalSalesRepId) {
            this.description = description;
            this.settlementDate = settlementDate;
            this.invoices = invoices;
            this.medicalSalesRepId = medicalSalesRepId;
        }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public LocalDate getSettlementDate() { return settlementDate; }
        public void setSettlementDate(LocalDate settlementDate) { this.settlementDate = settlementDate; }
        public List<Object> getInvoices() { return invoices; }
        public void setInvoices(List<Object> invoices) { this.invoices = invoices; }
        public String getMedicalSalesRepId() { return medicalSalesRepId; }
        public void setMedicalSalesRepId(String medicalSalesRepId) { this.medicalSalesRepId = medicalSalesRepId; }
    }
}
