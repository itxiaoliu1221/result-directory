package com.example.pickdispatch.meta;

import com.example.pickdispatch.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaController {

    private final MetaService metaService;

    @GetMapping("/warehouses")
    public ApiResponse<List<WarehouseDto>> warehouses() {
        return ApiResponse.ok(metaService.warehouses());
    }

    @GetMapping("/goods")
    public ApiResponse<List<GoodsDto>> goods() {
        return ApiResponse.ok(metaService.goods());
    }
}
