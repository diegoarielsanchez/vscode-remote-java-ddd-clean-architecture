package com.das.cleanddd.domain.catalog.usecases.services;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.lot.IStockLotRepository;
import com.das.cleanddd.domain.catalog.lot.LotStatus;
import com.das.cleanddd.domain.catalog.lot.ShelfLifePolicy;
import com.das.cleanddd.domain.catalog.lot.StockAllocator;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.reservation.entities.IStockReservationRepository;
import com.das.cleanddd.domain.catalog.usecases.dtos.AvailabilityOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.CreateProductInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ExcursionInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ExcursionOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.LotInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.LotQuantityInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.LotRecipientOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductIDDto;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductMapper;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductNamesInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ReceiveStockLotInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ReserveStockOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockLotOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockQuantityInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockQuantityOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.UpdateProductInputDTO;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UseCaseOnlyInput;

@Service
public class ProductUseCaseFactory {

    private final ProductMapper productMapper = new ProductMapper();

    private final CreateProductUseCase createProductUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final ActivateProductUseCase activateProductUseCase;
    private final DeactivateProductUseCase deactivateProductUseCase;
    private final GetProductByIdUseCase getProductByIdUseCase;
    private final FindProductsByNameUseCase findProductsByNameUseCase;
    private final CheckProductAvailabilityUseCase checkProductAvailabilityUseCase;
    private final ReserveProductStockUseCase reserveProductStockUseCase;
    private final ReleaseProductStockUseCase releaseProductStockUseCase;
    private final ReceiveStockLotUseCase receiveStockLotUseCase;
    private final ListStockLotsUseCase listStockLotsUseCase;
    private final ChangeLotQuarantineUseCase quarantineLotUseCase;
    private final ChangeLotQuarantineUseCase liftLotQuarantineUseCase;
    private final FindLotRecipientsUseCase findLotRecipientsUseCase;
    private final RecordExcursionUseCase recordExcursionUseCase;
    private final RejectLotUseCase rejectLotUseCase;

    public ProductUseCaseFactory(IProductRepository productRepository, IStockLotRepository lots,
                                 IStockReservationRepository reservations, IProductEventPublisher eventPublisher,
                                 UnitOfWork unitOfWork, ShelfLifePolicy shelfLifePolicy) {
        Clock clock = Clock.systemUTC();
        StockAllocator allocator = new StockAllocator(lots, shelfLifePolicy, clock);
        this.createProductUseCase = new CreateProductUseCase(productRepository, lots, productMapper, eventPublisher, unitOfWork, clock);
        this.updateProductUseCase = new UpdateProductUseCase(productRepository, productMapper, eventPublisher, unitOfWork);
        this.activateProductUseCase = new ActivateProductUseCase(productRepository, eventPublisher, unitOfWork);
        this.deactivateProductUseCase = new DeactivateProductUseCase(productRepository, eventPublisher, unitOfWork);
        this.getProductByIdUseCase = new GetProductByIdUseCase(productRepository, productMapper);
        this.findProductsByNameUseCase = new FindProductsByNameUseCase(productRepository, productMapper);
        this.checkProductAvailabilityUseCase = new CheckProductAvailabilityUseCase(productRepository, allocator);
        this.reserveProductStockUseCase = new ReserveProductStockUseCase(productRepository, lots, allocator, eventPublisher, unitOfWork);
        this.releaseProductStockUseCase = new ReleaseProductStockUseCase(productRepository, lots, eventPublisher, unitOfWork);
        this.receiveStockLotUseCase = new ReceiveStockLotUseCase(productRepository, lots, eventPublisher, unitOfWork,
                shelfLifePolicy, clock);
        this.listStockLotsUseCase = new ListStockLotsUseCase(productRepository, lots, shelfLifePolicy, clock);
        this.quarantineLotUseCase = new ChangeLotQuarantineUseCase(productRepository, lots, eventPublisher, unitOfWork,
                shelfLifePolicy, clock, LotStatus.QUARANTINED);
        this.liftLotQuarantineUseCase = new ChangeLotQuarantineUseCase(productRepository, lots, eventPublisher, unitOfWork,
                shelfLifePolicy, clock, LotStatus.AVAILABLE);
        this.findLotRecipientsUseCase = new FindLotRecipientsUseCase(productRepository, lots, reservations);
        this.recordExcursionUseCase = new RecordExcursionUseCase(productRepository, lots, eventPublisher, unitOfWork,
                shelfLifePolicy, clock);
        this.rejectLotUseCase = new RejectLotUseCase(productRepository, lots, eventPublisher, unitOfWork,
                shelfLifePolicy, clock);
    }

    public UseCase<CreateProductInputDTO, ProductOutputDTO> getCreateProductUseCase() {
        return createProductUseCase;
    }
    public UseCase<UpdateProductInputDTO, ProductOutputDTO> getUpdateProductUseCase() {
        return updateProductUseCase;
    }
    public UseCaseOnlyInput<ProductIDDto> getActivateProductUseCase() {
        return activateProductUseCase;
    }
    public UseCaseOnlyInput<ProductIDDto> getDeactivateProductUseCase() {
        return deactivateProductUseCase;
    }
    public UseCase<ProductIDDto, ProductOutputDTO> getGetProductByIdUseCase() {
        return getProductByIdUseCase;
    }
    public UseCase<ProductNamesInputDTO, List<ProductOutputDTO>> getFindProductsByNameUseCase() {
        return findProductsByNameUseCase;
    }
    public UseCase<StockQuantityInputDTO, AvailabilityOutputDTO> getCheckProductAvailabilityUseCase() {
        return checkProductAvailabilityUseCase;
    }
    public UseCase<StockQuantityInputDTO, ReserveStockOutputDTO> getReserveProductStockUseCase() {
        return reserveProductStockUseCase;
    }
    public UseCase<LotQuantityInputDTO, StockQuantityOutputDTO> getReleaseProductStockUseCase() {
        return releaseProductStockUseCase;
    }
    public UseCase<ReceiveStockLotInputDTO, StockLotOutputDTO> getReceiveStockLotUseCase() {
        return receiveStockLotUseCase;
    }
    public UseCase<ProductIDDto, List<StockLotOutputDTO>> getListStockLotsUseCase() {
        return listStockLotsUseCase;
    }
    public UseCase<LotInputDTO, StockLotOutputDTO> getQuarantineLotUseCase() {
        return quarantineLotUseCase;
    }
    public UseCase<LotInputDTO, StockLotOutputDTO> getLiftLotQuarantineUseCase() {
        return liftLotQuarantineUseCase;
    }
    public UseCase<LotInputDTO, List<LotRecipientOutputDTO>> getFindLotRecipientsUseCase() {
        return findLotRecipientsUseCase;
    }
    public UseCase<ExcursionInputDTO, ExcursionOutputDTO> getRecordExcursionUseCase() {
        return recordExcursionUseCase;
    }
    public UseCase<LotInputDTO, StockLotOutputDTO> getRejectLotUseCase() {
        return rejectLotUseCase;
    }
}
