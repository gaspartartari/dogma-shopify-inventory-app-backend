package com.tartaritech.inventory_sync.dtos;

import com.tartaritech.inventory_sync.entities.ControlledSKu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ControlledSkuDTO {
    
    @NotBlank(message = "SKU é obrigatório")
    @Size(max = 100, message = "SKU deve ter no máximo 100 caracteres")
    private String sku;
    
    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 255, message = "Nome deve ter no máximo 255 caracteres")
    private String name;
    
    public ControlledSkuDTO(ControlledSKu entity) {
        this.sku = entity.getSku();
        this.name = entity.getName();
    }
}

