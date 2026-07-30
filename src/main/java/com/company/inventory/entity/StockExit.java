package com.company.inventory.entity;


import io.jmix.core.entity.annotation.JmixGeneratedValue;

import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;


import java.util.Date;
import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "INV_STOCK_EXIT")
public class StockExit {

    @Id
    @Column(name = "ID")
    @JmixGeneratedValue
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @NotNull
    @Column(name = "EXIT_DATE", nullable = false)
    @Temporal(TemporalType.DATE)
    private Date exitDate;

    @Column(name = "VOUCHER", length = 100)
    private String voucher;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EMPLOYEE_ID")
    private Employee employee;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "NOTE1", length = 255)
    private String note1;

    @Column(name = "NOTE2", length = 255)
    private String note2;

    @NotNull
    @Column(name = "QUANTITY", nullable = false)
    private Integer quantity;

    @Column(name = "REASON", length = 500)
    private String reason;

    @Column(name = "CANCELLED")
    private Boolean cancelled = false;

    @org.springframework.data.annotation.CreatedBy
    @Column(name = "CREATED_BY")
    private String createdBy;

    @org.springframework.data.annotation.CreatedDate
    @Column(name = "CREATED_DATE")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;

    @org.springframework.data.annotation.LastModifiedBy
    @Column(name = "LAST_MODIFIED_BY")
    private String lastModifiedBy;

    @org.springframework.data.annotation.LastModifiedDate
    @Column(name = "LAST_MODIFIED_DATE")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastModifiedDate;

    @InstanceName
    @DependsOnProperties({"product", "exitDate", "quantity"})
    public String getDisplayName() {
        String productCode = product != null ? product.getCode() : "?";
        String date = exitDate != null ? exitDate.toString() : "?";
        return productCode + " / " + date + " / -" + (quantity != null ? quantity : 0);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Date getExitDate() { return exitDate; }
    public void setExitDate(Date exitDate) { this.exitDate = exitDate; }

    public String getVoucher() { return voucher; }
    public void setVoucher(String voucher) { this.voucher = voucher; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getNote1() { return note1; }
    public void setNote1(String note1) { this.note1 = note1; }

    public String getNote2() { return note2; }
    public void setNote2(String note2) { this.note2 = note2; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Boolean getCancelled() { return cancelled; }
    public void setCancelled(Boolean cancelled) { this.cancelled = cancelled; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    public String getLastModifiedBy() { return lastModifiedBy; }
    public void setLastModifiedBy(String lastModifiedBy) { this.lastModifiedBy = lastModifiedBy; }

    public Date getLastModifiedDate() { return lastModifiedDate; }
    public void setLastModifiedDate(Date lastModifiedDate) { this.lastModifiedDate = lastModifiedDate; }
}
