Feature: Transaksi penjualan dan pengelolaan stok produk

  Scenario: Penjualan berhasil mengurangi stok produk
    Given produk memiliki stok 10
    When kasir menjual produk sebanyak 3
    Then stok produk menjadi 7
    And transaksi penjualan tersimpan

  Scenario: Penjualan gagal jika stok tidak mencukupi
    Given produk memiliki stok 2
    When kasir menjual produk sebanyak 5
    Then transaksi penjualan ditolak
    And stok produk tetap 2
    And tidak ada transaksi penjualan tersimpan