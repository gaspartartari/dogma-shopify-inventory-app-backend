package com.tartaritech.inventory_sync.entities;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@Entity
@Table(name = "tb_controlled_sku")
public class ControlledSKu {
    
    @Id
    private String sku;
    private String name;

    @OneToMany(mappedBy = "controlledSku")
    private List<Product> nextItems = new ArrayList<>();
    
}
