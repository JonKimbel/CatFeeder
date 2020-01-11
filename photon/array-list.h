// A dynamically sized array. Example usage:
//
// ArrayList<char> arrayList(/* initialLength = */ 10);
//
// arrayList.add('H');
// arrayList.add('i');
// arrayList.add('!');
//
// for (int i = 0; i < arrayList.length; i++) {
//   printf("Data at %d: %s\n", i, arrayList.data[i]);
// }

#ifndef ARRAY_LIST_H
#define ARRAY_LIST_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>

template <typename T>
class ArrayList {
  public:
    int length;
    T *data;

    ArrayList() : ArrayList(0) {}

    // Initialize an ArrayList with the given starting length. The starting
    // length is merely a guess at how large the inner array will need to be, if
    // you don't care just pass 0.
    ArrayList(int initialLength);

    ~ArrayList();

    // Add an item to the end of an ArrayList, resizing if necessary.
    // Returns false if memory could not be allocated for the resize.
    bool add(T item);

    // Clear all data out of the ArrayList and re-initialize it with the given
    // length.
    void clear(int initialLength = 0);
  private:
    int _allocatedLength;

    void _init(int initialLength);
};

template <typename T>
ArrayList<T>::ArrayList(int initialLength) {
  _init(initialLength);
}

template <typename T>
ArrayList<T>::~ArrayList() {
  free(data);
}

template <typename T>
bool ArrayList<T>::add(T item) {
  if (_allocatedLength <= length) {
    // There's not enough allocated space, resize the data array.
    int newAllocatedLength = _allocatedLength <= 0
        ? 1 : _allocatedLength * 2;
    T* newData = (T *) malloc(newAllocatedLength * sizeof(T));
    if (newData == NULL) {
      // Can't allocate any more space.
      return false;
    }
    _allocatedLength = newAllocatedLength;

    // Copy existing data to the new array.
    for (int i = 0; i < length; i++) {
      newData[i] = data[i];
    }

    // Free the space allocated to the old array and update the data pointer.
    free(data);
    data = newData;
  }

  // Update the length and set the new value.
  length++;
  data[length - 1] = item;
  return true;
}

template <typename T>
void ArrayList<T>::clear(int initialLength) {
  free(data);
  _init(initialLength);
}

// Private methods.

template <typename T>
void ArrayList<T>::_init(int initialLength) {
  _allocatedLength = initialLength < 0 ? 0 : initialLength;
  length = 0;
  data = (T *) malloc(_allocatedLength * sizeof(T));
}

#endif // ARRAY_LIST_H
