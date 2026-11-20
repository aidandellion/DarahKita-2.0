const { BLOOD_TYPES } = require('../config');

const isBloodType = (t) => BLOOD_TYPES.includes(t);

/** Same doc id as StockActivity / DummyDataSeeder: "C001" + "A+" -> "C001_APlus". */
function stockDocId(centreID, bloodType) {
  return `${centreID}_${bloodType.replaceAll('+', 'Plus').replaceAll('-', 'Minus')}`;
}

module.exports = { isBloodType, stockDocId };
