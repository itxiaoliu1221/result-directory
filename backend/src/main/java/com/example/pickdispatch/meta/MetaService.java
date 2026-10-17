package com.example.pickdispatch.meta;

import com.example.pickdispatch.common.BusinessException;
import com.example.pickdispatch.goods.Goods;
import com.example.pickdispatch.goods.GoodsRepository;
import com.example.pickdispatch.warehouse.Warehouse;
import com.example.pickdispatch.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetaService {

    private final WarehouseRepository warehouseRepository;
    private final GoodsRepository goodsRepository;

    @Transactional(readOnly = true)
    public List<WarehouseDto> warehouses() {
        return warehouseRepository.findAllByOrderByIdAsc().stream()
                .map(w -> new WarehouseDto(w.getId(), w.getCode(), w.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GoodsDto> goods() {
        return goodsRepository.findAllByOrderByIdAsc().stream()
                .map(g -> new GoodsDto(g.getId(), g.getCode(), g.getName(), g.getCategory()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Warehouse requireWarehouse(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("仓库不存在: " + id));
    }

    @Transactional(readOnly = true)
    public Goods requireGoods(Long id) {
        return goodsRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("货物不存在: " + id));
    }

    @Transactional(readOnly = true)
    public Map<Long, Warehouse> warehouseMap() {
        return warehouseRepository.findAll().stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public Map<Long, Goods> goodsMap() {
        return goodsRepository.findAll().stream()
                .collect(Collectors.toMap(Goods::getId, Function.identity()));
    }
}
